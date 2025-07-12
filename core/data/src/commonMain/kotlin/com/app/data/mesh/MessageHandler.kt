package com.app.data.mesh

import com.app.data.crypto.MessagePadding
import com.app.data.model.BitchatPacket
import com.app.data.model.DeliveryAckDto
import com.app.data.model.MessageType
import com.app.data.model.ReadReceiptDto
import com.app.data.utils.MessagePayloadSerializer
import com.app.data.utils.SpecialRecipients
import com.app.domain.model.BitchatMessage
import com.app.domain.model.DeliveryAck
import com.app.domain.model.ReadReceipt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.random.Random


class MessageHandler(
    private val myPeerId: String,
    private val scope: CoroutineScope,
    private val delegate: MessageHandlerDelegate,
    private val messagePayloadSerializer: MessagePayloadSerializer,
) {

    suspend fun handleAnnounce(packet: BitchatPacket, peerId: String): Boolean {
        if (peerId == myPeerId) return false
        
        val nickname = packet.payload.decodeToString()
        val isFirstAnnounce = delegate.addOrUpdatePeer(peerId, nickname)
        
        if (packet.ttl > 1u) {
            val relayPacket = packet.copy(ttl = (packet.ttl - 1u).toUByte())
            delay(Random.nextLong(100, 300))
            delegate.relayPacket(relayPacket)
        }
        return isFirstAnnounce
    }

    suspend fun handleMessage(packet: BitchatPacket, peerId: String) {
        if (peerId == myPeerId) return

        val recipientIdBytes = packet.recipientId
        
        if (recipientIdBytes == null || recipientIdBytes.contentEquals(SpecialRecipients.BROADCAST)) {
            handleBroadcastMessage(packet, peerId)
        } else if (recipientIdBytes.decodeToString().trimEnd('\u0000') == myPeerId) {
            handlePrivateMessage(packet, peerId)
        } else if (packet.ttl > 0u) {
            relayMessage(packet)
        }
    }
    
    private suspend fun handleBroadcastMessage(packet: BitchatPacket, peerId: String) {
        val message = messagePayloadSerializer.decodeFromPayload(packet.payload) ?: return

        // Проверка на cover traffic
        if (message.content.startsWith("☂DUMMY☂")) return

        delegate.updatePeerNickname(peerId, message.sender)

        val channel = message.channel
        val encryptedContent = message.encryptedContent

        val finalContent = if (channel != null && message.isEncrypted && encryptedContent != null) {
            delegate.decryptChannelMessage(encryptedContent, channel)
                ?: "[Encrypted message - password required]"
        } else {
            message.content
        }

        val finalMessage = message.copy(
            content = finalContent,
            senderPeerId = peerId
        )

        delegate.onMessageReceived(finalMessage)
        relayMessage(packet)
    }

    private suspend fun handlePrivateMessage(packet: BitchatPacket, peerId: String) {
        if (packet.signature != null && !delegate.verifySignature(packet, peerId)) {
            return
        }

        val decryptedData = delegate.decryptFromPeer(packet.payload, peerId) ?: return
        val unpaddedData = MessagePadding.unpad(decryptedData)
        val message = messagePayloadSerializer.decodeFromPayload(unpaddedData) ?: return

        if (message.content.startsWith("☂DUMMY☂")) return

        delegate.updatePeerNickname(peerId, message.sender)

        val finalMessage = message.copy(senderPeerId = peerId)
        delegate.onMessageReceived(finalMessage)
        
        sendDeliveryAck(finalMessage, peerId)
    }
    
    suspend fun handleLeave(packet: BitchatPacket, peerId: String) {
        val content = packet.payload.decodeToString()
        
        if (content.startsWith("#")) {
            delegate.onChannelLeave(content, peerId)
        } else {
            val nickname = delegate.getPeerNickname(peerId)
            delegate.removePeer(peerId)
            if (nickname != null) {
                delegate.onPeerDisconnected(nickname)
            }
        }
        
        if (packet.ttl > 1u) {
            val relayPacket = packet.copy(ttl = (packet.ttl - 1u).toUByte())
            delegate.relayPacket(relayPacket)
        }
    }
    
    suspend fun handleDeliveryAck(packet: BitchatPacket, peerId: String) {
        val recipientId = packet.recipientId?.decodeToString()?.trimEnd('\u0000')
        if (recipientId == myPeerId) {
            val decryptedData = delegate.decryptFromPeer(packet.payload, peerId) ?: return
            val ack = DeliveryAckDto.decode(decryptedData)
            if (ack != null) {
                delegate.onDeliveryAckReceived(ack)
            }
        } else if (packet.ttl > 0u) {
            relayMessage(packet)
        }
    }


    suspend fun handleReadReceipt(packet: BitchatPacket, peerId: String) {
        val recipientId = packet.recipientId?.decodeToString()?.trimEnd('\u0000')
        if (recipientId == myPeerId) {
            val decryptedData = delegate.decryptFromPeer(packet.payload, peerId) ?: return
            val receipt = ReadReceiptDto.decode(decryptedData)

            if (receipt != null) {
                delegate.onReadReceiptReceived(receipt)
            }
        } else if (packet.ttl > 0u) {
            relayMessage(packet)
        }
    }
    
    private suspend fun relayMessage(packet: BitchatPacket) {
        if (packet.ttl == 0.toUByte()) return
        
        val relayPacket = packet.copy(ttl = (packet.ttl - 1u).toUByte())

        val networkSize = delegate.getNetworkSize() ?: 1
        val relayProb = when {
            networkSize <= 10 -> 1.0
            networkSize <= 30 -> 0.85
            networkSize <= 50 -> 0.7
            networkSize <= 100 -> 0.55
            else -> 0.4
        }
        
        val shouldRelay = relayPacket.ttl >= 4u || networkSize <= 3 || Random.nextDouble() < relayProb
        
        if (shouldRelay) {
            delay(Random.nextLong(50, 500))
            delegate.relayPacket(relayPacket)
        }
    }

    private fun sendDeliveryAck(message: BitchatMessage, senderPeerId: String) {
        scope.launch {
            val nickname = delegate.getMyNickname() ?: myPeerId
            val ack = DeliveryAckDto(
                originalMessageID = message.id,
                recipientID = myPeerId,
                recipientNickname = nickname,
                hopCount = 0u
            )

            val ackPayload = ack.encode()
            val encryptedPayload = delegate.encryptForPeer(ackPayload, senderPeerId) ?: return@launch

            val packet = BitchatPacket(
                type = MessageType.DELIVERY_ACK.value,
                senderId = myPeerId.encodeToByteArray(),
                recipientId = senderPeerId.encodeToByteArray(),
                timestamp = Clock.System.now().toEpochMilliseconds().toULong(),
                payload = encryptedPayload,
                ttl = 3u
            )
            delegate.sendPacket(packet)
        }
    }

    fun getDebugInfo(): String {
        return buildString {
            appendLine("=== Message Handler Debug Info ===")
            appendLine("Handler Scope Active: ${scope.isActive}")
            appendLine("My Peer ID: $myPeerId")
        }
    }

}


/**
 * Delegate interface for message handler callbacks
 */
interface MessageHandlerDelegate {
    // Peer management
    fun addOrUpdatePeer(peerID: String, nickname: String): Boolean
    fun removePeer(peerID: String)
    fun updatePeerNickname(peerID: String, nickname: String)
    fun getPeerNickname(peerID: String): String?
    fun getNetworkSize(): Int
    fun getMyNickname(): String?

    // Packet operations
    fun sendPacket(packet: BitchatPacket)
    fun relayPacket(packet: BitchatPacket)
    fun getBroadcastRecipient(): ByteArray

    // Cryptographic operations
    fun verifySignature(packet: BitchatPacket, peerID: String): Boolean
    fun encryptForPeer(data: ByteArray, recipientPeerID: String): ByteArray?
    fun decryptFromPeer(encryptedData: ByteArray, senderPeerID: String): ByteArray?

    // Message operations
    fun decryptChannelMessage(encryptedContent: ByteArray, channel: String): String?

    // Callbacks
    fun onMessageReceived(message: BitchatMessage)
    fun onChannelLeave(channel: String, fromPeer: String)
    fun onPeerDisconnected(nickname: String)
    fun onDeliveryAckReceived(ack: DeliveryAck)
    fun onReadReceiptReceived(receipt: ReadReceipt)
}