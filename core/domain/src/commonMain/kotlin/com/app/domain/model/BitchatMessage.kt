package com.app.domain.model

import kotlinx.datetime.Instant

data class BitchatMessage(
    val id: String,
    val sender: String,
    val content: String,
    val timestamp: Instant,
    val isRelay: Boolean = false,
    val originalSender: String? = null,
    val isPrivate: Boolean,
    val recipientNickname: String? = null,
    val senderPeerId: String,
    val mentions: List<String> = emptyList(),
    val channel: String? = null,
    val encryptedContent: ByteArray? = null,
    val isEncrypted: Boolean = false,

    val deliveryStatus: DeliveryStatus? = if (isPrivate) DeliveryStatus.Sending else DeliveryStatus.Sent,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as BitchatMessage

        if (isPrivate != other.isPrivate) return false
        if (isRelay != other.isRelay) return false
        if (isEncrypted != other.isEncrypted) return false
        if (id != other.id) return false
        if (sender != other.sender) return false
        if (senderPeerId != other.senderPeerId) return false
        if (content != other.content) return false
        if (timestamp != other.timestamp) return false
        if (channel != other.channel) return false
        if (recipientNickname != other.recipientNickname) return false
        if (mentions != other.mentions) return false
        if (originalSender != other.originalSender) return false
        if (deliveryStatus != other.deliveryStatus) return false
        if (!encryptedContent.contentEquals(other.encryptedContent)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isPrivate.hashCode()
        result = 31 * result + isRelay.hashCode()
        result = 31 * result + isEncrypted.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + sender.hashCode()
        result = 31 * result + senderPeerId.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (channel?.hashCode() ?: 0)
        result = 31 * result + (recipientNickname?.hashCode() ?: 0)
        result = 31 * result + mentions.hashCode()
        result = 31 * result + (originalSender?.hashCode() ?: 0)
        result = 31 * result + deliveryStatus.hashCode()
        result = 31 * result + (encryptedContent?.contentHashCode() ?: 0)
        return result
    }
}