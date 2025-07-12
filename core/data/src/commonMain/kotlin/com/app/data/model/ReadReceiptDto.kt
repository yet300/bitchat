package com.app.data.model

import com.app.domain.model.ReadReceipt
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ReadReceiptDto(
    val originalMessageID: String,
    val receiptID: String,
    val readerID: String,
    val readerNickname: String,
    val timestamp: Instant
){
    fun encode(): ByteArray {
        val json = Json { ignoreUnknownKeys = true }
        return json.encodeToString(this).encodeToByteArray()
    }

    companion object {
        fun decode(data: ByteArray): ReadReceipt? {
            return try {
                val json = Json { ignoreUnknownKeys = true }
                json.decodeFromString<ReadReceipt>(data.decodeToString())
            } catch (e: Exception) {
                null
            }
        }
    }
}