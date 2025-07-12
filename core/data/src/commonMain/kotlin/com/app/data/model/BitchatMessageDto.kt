package com.app.data.model

import com.app.data.utils.ByteArraySerializer
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable



@Serializable
data class BitchatMessageDto(
    val id: String,
    val sender: String,
    val content: String,
    val timestamp: Instant,
    val isRelay: Boolean,
    val originalSender: String?,
    val isPrivate: Boolean,
    val recipientNickname: String?,
    val senderPeerId: String,
    val mentions: List<String>,
    val channel: String?,
    val isEncrypted: Boolean,
    @Serializable(with = ByteArraySerializer::class)
    val encryptedContent: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as BitchatMessageDto

        if (isRelay != other.isRelay) return false
        if (isPrivate != other.isPrivate) return false
        if (isEncrypted != other.isEncrypted) return false
        if (id != other.id) return false
        if (sender != other.sender) return false
        if (content != other.content) return false
        if (timestamp != other.timestamp) return false
        if (originalSender != other.originalSender) return false
        if (recipientNickname != other.recipientNickname) return false
        if (senderPeerId != other.senderPeerId) return false
        if (mentions != other.mentions) return false
        if (channel != other.channel) return false
        if (!encryptedContent.contentEquals(other.encryptedContent)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isRelay.hashCode()
        result = 31 * result + isPrivate.hashCode()
        result = 31 * result + isEncrypted.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + sender.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (originalSender?.hashCode() ?: 0)
        result = 31 * result + (recipientNickname?.hashCode() ?: 0)
        result = 31 * result + senderPeerId.hashCode()
        result = 31 * result + mentions.hashCode()
        result = 31 * result + (channel?.hashCode() ?: 0)
        result = 31 * result + (encryptedContent?.contentHashCode() ?: 0)
        return result
    }

}