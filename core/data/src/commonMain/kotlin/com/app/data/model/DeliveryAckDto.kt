package com.app.data.model

import com.app.domain.model.DeliveryAck
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class DeliveryAckDto(
    val originalMessageID: String,
    val ackID: String = Uuid.random().toString(),
    val recipientID: String,
    val recipientNickname: String,
    val timestamp: Instant = Clock.System.now(),
    val hopCount: UByte
){
    fun encode(): ByteArray {
        val json = Json { ignoreUnknownKeys = true }
        return json.encodeToString(this).encodeToByteArray()
    }

    companion object {
        fun decode(data: ByteArray): DeliveryAck? {
            return try {
                val json = Json { ignoreUnknownKeys = true }
                json.decodeFromString<DeliveryAck>(data.decodeToString())
            } catch (e: Exception) {
                e.stackTraceToString()
                null
            }
        }
    }
}