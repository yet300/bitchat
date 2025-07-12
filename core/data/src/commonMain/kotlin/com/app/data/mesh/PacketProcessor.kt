package com.app.data.mesh

import com.app.data.model.BitchatPacket
import com.app.data.model.MessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PacketProcessor(
    private val myPeerId: String,
    private val scope: CoroutineScope,
    private val delegate: PacketProcessorDelegate
) {

    fun processPacket(packet: BitchatPacket, peerId: String) {
        scope.launch {
            handleReceivedPacket(packet, peerId)
        }
    }

    private suspend fun handleReceivedPacket(packet: BitchatPacket, peerId: String) {
        if (!delegate.validatePacketSecurity(packet, peerId)) {
            return
        }

        delegate.updatePeerLastSeen(peerId)

        when (MessageType.fromValue(packet.type)) {
            MessageType.KEY_EXCHANGE -> handleKeyExchange(packet, peerId)
            MessageType.ANNOUNCE -> delegate.handleAnnounce(packet, peerId)
            MessageType.MESSAGE -> delegate.handleMessage(packet, peerId)
            MessageType.LEAVE -> delegate.handleLeave(packet, peerId)
            MessageType.FRAGMENT_START,
            MessageType.FRAGMENT_CONTINUE,
            MessageType.FRAGMENT_END -> handleFragment(packet, peerId)

            MessageType.DELIVERY_ACK -> delegate.handleDeliveryAck(packet, peerId)
            MessageType.READ_RECEIPT -> delegate.handleReadReceipt(packet, peerId)
            else -> {
                print("Unknown message type: ${packet.type}")
            }
        }
    }

    private suspend fun handleKeyExchange(packet: BitchatPacket, peerID: String) {
        val success = delegate.handleKeyExchange(packet, peerID)

        if (success) {
            delay(100)
            delegate.sendAnnouncementToPeer(peerID)

            delay(500)
            delegate.sendCachedMessages(peerID)
        }
    }

    private suspend fun handleFragment(packet: BitchatPacket, peerId: String) {
        val reassembledPacket = delegate.handleFragment(packet)
        if (reassembledPacket != null) {
            handleReceivedPacket(reassembledPacket, peerId)
        }

        if (packet.ttl > 0u) {
            val relayPacket = packet.copy(ttl = (packet.ttl - 1u).toUByte())
            delegate.relayPacket(relayPacket)
        }
    }

    fun getDebugInfo(): String {
        return buildString {
            appendLine("=== Packet Processor Debug Info ===")
            appendLine("Processor Scope Active: ${scope.isActive}")
            appendLine("My Peer ID: $myPeerId")
        }
    }

    fun shutdown() {
        scope.cancel()
    }
}


/**
 * Delegate interface for packet processor callbacks
 */
interface PacketProcessorDelegate {
    // Security validation
    fun validatePacketSecurity(packet: BitchatPacket, peerID: String): Boolean

    // Peer management
    fun updatePeerLastSeen(peerID: String)

    // Message type handlers
    fun handleKeyExchange(packet: BitchatPacket, peerID: String): Boolean
    fun handleAnnounce(packet: BitchatPacket, peerID: String)
    fun handleMessage(packet: BitchatPacket, peerID: String)
    fun handleLeave(packet: BitchatPacket, peerID: String)
    fun handleFragment(packet: BitchatPacket): BitchatPacket?
    fun handleDeliveryAck(packet: BitchatPacket, peerID: String)
    fun handleReadReceipt(packet: BitchatPacket, peerID: String)

    // Communication
    fun sendAnnouncementToPeer(peerID: String)
    fun sendCachedMessages(peerID: String)
    fun relayPacket(packet: BitchatPacket)
}