package com.app.data.mesh

import com.app.data.model.BitchatPacket
import com.app.data.model.MessageType
import com.app.data.utils.SpecialRecipients
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes


class StoreForwardManager(
    private val scope: CoroutineScope,
    private val delegate: StoreForwardDelegate
) {
    private val messageCacheTimeout = 12.hours
    private val maxCachedMessages = 100
    private val maxCachedMessagesForFavorites = 1000
    private val cleanupInterval = 10.minutes

    private data class StoredMessage(
        val packet: BitchatPacket,
        val timestamp: Instant,
        val messageId: String,
        val isForFavorite: Boolean
    )

    private val messageCache = mutableListOf<StoredMessage>()
    private val favoriteMessageQueue = mutableMapOf<String, MutableList<StoredMessage>>()
    private val deliveredMessages = mutableSetOf<String>()
    private val cachedMessagesSentToPeer = mutableSetOf<String>()
    private val mutex = Mutex()

    init {
        startPeriodicCleanup()
    }

    suspend fun cachePacket(packet: BitchatPacket, messageId: String) {
        val packetType = MessageType.fromValue(packet.type)
        if (packetType in listOf(
                MessageType.KEY_EXCHANGE,
                MessageType.ANNOUNCE,
                MessageType.LEAVE
            )
        ) {
            return
        }
        if (packet.recipientId?.contentEquals(SpecialRecipients.BROADCAST) == true) {
            return
        }

        val recipientPeerId = packet.recipientId?.decodeToString()?.trimEnd('\u0000')
        if (recipientPeerId.isNullOrEmpty()) return

        val isForFavorite = delegate.isFavorite(recipientPeerId)

        val storedMessage = StoredMessage(
            packet,
            Clock.System.now(),
            messageId,
            isForFavorite
        )

        mutex.withLock {
            if (isForFavorite) {
                val queue = favoriteMessageQueue.getOrPut(recipientPeerId) { mutableListOf() }
                queue.add(storedMessage)
                if (queue.size > maxCachedMessagesForFavorites) {
                    queue.removeAt(0)
                }
            } else {
                messageCache.add(storedMessage)
                if (messageCache.size > maxCachedMessages) {
                    messageCache.removeAt(0)
                }
            }
        }
    }

    suspend fun sendCachedMessages(peerId: String) {
        if (cachedMessagesSentToPeer.contains(peerId)) return
        cachedMessagesSentToPeer.add(peerId)

        scope.launch {
            val messagesToSend = mutableListOf<StoredMessage>()

            mutex.withLock {
                // Извлекаем из очереди для избранных
                favoriteMessageQueue.remove(peerId)?.let { favoriteMessages ->
                    messagesToSend.addAll(favoriteMessages.filter { !deliveredMessages.contains(it.messageId) })
                }
                // Извлекаем из обычной очереди
                val recipientMessages = messageCache.filter {
                    val packetRecipientId =
                        it.packet.recipientId?.decodeToString()?.trimEnd('\u0000')
                    !deliveredMessages.contains(it.messageId) && packetRecipientId == peerId
                }
                messagesToSend.addAll(recipientMessages)
            }

            messagesToSend.sortBy { it.timestamp }

            // Помечаем как доставленные и отправляем
            val idsToRemove = messagesToSend.map { it.messageId }
            mutex.withLock { deliveredMessages.addAll(idsToRemove) }

            messagesToSend.forEachIndexed { index, message ->
                delay(100L * index)
                delegate.sendPacket(message.packet)
            }

            // Удаляем из кэша
            mutex.withLock {
                messageCache.removeAll { idsToRemove.contains(it.messageId) }
            }
        }
    }

    suspend fun shouldCacheForPeer(recipientPeerId: String): Boolean {
        val isOffline = !delegate.isPeerOnline(recipientPeerId)
        val isFavorite = delegate.isFavorite(recipientPeerId)
        return isOffline && isFavorite
    }

    private fun startPeriodicCleanup() {
        scope.launch {
            while (isActive) {
                delay(cleanupInterval)
                mutex.withLock {
                    cleanupMessageCache()
                    cleanupDeliveredMessages()
                }
            }
        }
    }

    private fun cleanupMessageCache() {
        val cutoffTime = Clock.System.now() - messageCacheTimeout
        messageCache.removeAll { !it.isForFavorite && it.timestamp < cutoffTime }
    }

    private fun cleanupDeliveredMessages() {
        if (deliveredMessages.size > 1000) deliveredMessages.clear()
        if (cachedMessagesSentToPeer.size > 200) cachedMessagesSentToPeer.clear()
    }

    suspend fun clearAllCache() {
        mutex.withLock {
            messageCache.clear()
            favoriteMessageQueue.clear()
            deliveredMessages.clear()
            cachedMessagesSentToPeer.clear()
        }
    }

    fun shutdown() {
        scope.cancel()
    }
}

/**
 * Delegate interface for store-forward manager callbacks
 */
interface StoreForwardDelegate {
    suspend fun isFavorite(peerId: String): Boolean
    suspend fun isPeerOnline(peerId: String): Boolean
    suspend fun sendPacket(packet: BitchatPacket)
}