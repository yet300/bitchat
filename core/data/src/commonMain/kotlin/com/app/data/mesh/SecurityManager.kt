package com.app.data.mesh

import com.app.data.crypto.EncryptionService
import com.app.data.model.BitchatPacket
import com.app.data.model.MessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.minutes

class SecurityManager(
    private val encryptionService: EncryptionService,
    private val myPeerId: String,
    private val scope: CoroutineScope,
    private val delegate: SecurityManagerDelegate
) {
    private val messageTimeout = 5.minutes
    private val cleanupInterval = 5.minutes
    private val maxProcessedMessages = 10000
    private val maxProcessedKeyExchanges = 1000


    private val processedMessages = mutableSetOf<String>()
    private val processedKeyExchanges = mutableSetOf<String>()
    private val messageTimestamps = mutableMapOf<String, Long>()
    private val mutex = Mutex()

    init {
        startPeriodicCleanup()
    }

    suspend fun validatePacket(packet: BitchatPacket): Boolean {
        val senderId = packet.senderId.decodeToString().trimEnd('\u0000')

        if (senderId == myPeerId) return false
        if (packet.ttl == 0.toUByte()) return false
        if (packet.payload.isEmpty()) return false


        val currentTime = Clock.System.now().toEpochMilliseconds()
        val timeDiff = kotlin.math.abs(currentTime - packet.timestamp.toLong())
        if (timeDiff > messageTimeout.inWholeMilliseconds) return false

        val messageID = generateMessageID(packet, senderId)
        mutex.withLock {
            if (processedMessages.contains(messageID)) {
                return@withLock false
            }
            processedMessages.add(messageID)
            messageTimestamps[messageID] = currentTime
            return@withLock true
        }
        return true
    }

    suspend fun handleKeyExchange(packet: BitchatPacket): Boolean {
        val senderId = packet.senderId.decodeToString().trimEnd('\u0000')
        if (senderId == myPeerId) return false
        if (packet.payload.isEmpty()) return false

        val exchangeKey = "$senderId-${packet.payload.take(16).toByteArray().contentHashCode()}"

        mutex.withLock {
            if (processedKeyExchanges.contains(exchangeKey)) return@withLock false
            processedKeyExchanges.add(exchangeKey)
        }

        return try {
            encryptionService.addPeerPublicKey(senderId, packet.payload)
            delegate.onKeyExchangeCompleted(senderId)
            true
        } catch (e: Exception) {
            // log error
            false
        }
    }

    suspend fun verifySignature(packet: BitchatPacket): Boolean {
        val senderId = packet.senderId.decodeToString().trimEnd('\u0000')
        val signature = packet.signature ?: return true

        return try {
            encryptionService.verify(signature, packet.payload, senderId)
        } catch (e: Exception) {
            false
        }
    }

    private fun generateMessageID(packet: BitchatPacket, peerID: String): String {
        val messageType = MessageType.fromValue(packet.type)
        return when (messageType) {
            MessageType.FRAGMENT_START, MessageType.FRAGMENT_CONTINUE, MessageType.FRAGMENT_END -> {
                "${packet.timestamp}-$peerID-${packet.type}-${packet.payload.contentHashCode()}"
            }

            else -> {
                val payloadHash = packet.payload.take(64).toByteArray().contentHashCode()
                "${packet.timestamp}-$peerID-$payloadHash"
            }
        }
    }

    private fun startPeriodicCleanup() {
        scope.launch {
            while (isActive) {
                delay(cleanupInterval)
                cleanupOldData()
            }
        }
    }

    private suspend fun cleanupOldData() = mutex.withLock {
        val cutoffTime =
            Clock.System.now().toEpochMilliseconds() - messageTimeout.inWholeMilliseconds

        val messagesToRemove = messageTimestamps.filter { it.value < cutoffTime }.map { it.key }
        messagesToRemove.forEach {
            messageTimestamps.remove(it)
            processedMessages.remove(it)
        }

        if (processedMessages.size > maxProcessedMessages) {
            val toRemove = processedMessages.take(processedMessages.size - maxProcessedMessages)
            processedMessages.removeAll(toRemove.toSet())
            toRemove.forEach { messageTimestamps.remove(it) }
        }

        if (processedKeyExchanges.size > maxProcessedKeyExchanges) {
            val toRemove =
                processedKeyExchanges.take(processedKeyExchanges.size - maxProcessedKeyExchanges)
            processedKeyExchanges.removeAll(toRemove.toSet())
        }
    }

    fun shutdown() {
        scope.cancel()
    }
}


interface SecurityManagerDelegate {
    suspend fun onKeyExchangeCompleted(peerId: String)
}