package com.app.domain.repository

import com.app.domain.model.BitchatMessage
import kotlinx.coroutines.flow.Flow

interface MessageRepository {

    fun getIncomingMessages(): Flow<BitchatMessage>

    suspend fun sendMessage(message: BitchatMessage): Boolean

    suspend fun sendPrivateMessage(message: BitchatMessage, toPeerId: String): Boolean

    suspend fun sendEncryptedChannelMessage(message: BitchatMessage, channel: String, key: ByteArray): Boolean

    suspend fun getHistoryForChannel(channel: String): List<BitchatMessage>
}