package com.app.data.utils

import com.app.domain.model.BitchatMessage
import com.app.domain.model.DeliveryStatus
import kotlinx.datetime.Instant
import okio.Buffer

object MessagePayloadSerializer {

    fun encodeToPayload(message: BitchatMessage): ByteArray {
        val buffer = Buffer()

        var flags: UByte = 0u
        if (message.isRelay) flags = flags or 0x01u
        if (message.isPrivate) flags = flags or 0x02u
        if (message.originalSender != null) flags = flags or 0x04u
        if (message.recipientNickname != null) flags = flags or 0x08u
        if (message.senderPeerId.isNotBlank()) flags = flags or 0x10u
        if (message.mentions.isNotEmpty()) flags = flags or 0x20u
        if (message.channel != null) flags = flags or 0x40u
        if (message.isEncrypted) flags = flags or 0x80u
        buffer.writeByte(flags.toInt())

        buffer.writeLong(message.timestamp.toEpochMilliseconds())
        buffer.writeVariableString(message.id)
        buffer.writeVariableString(message.sender)

        if (message.isEncrypted) {
            message.encryptedContent?.let {
                buffer.writeVariableByteArray(it, isShort = false)
            }
        } else {
            buffer.writeVariableString(message.content, isShort = false)
        }

        message.originalSender?.let { buffer.writeVariableString(it) }
        message.recipientNickname?.let { buffer.writeVariableString(it) }
        if (message.senderPeerId.isNotBlank()) {
            buffer.writeVariableString(message.senderPeerId)
        }
        if (message.mentions.isNotEmpty()) {
            buffer.writeByte(minOf(message.mentions.size, 255))
            message.mentions.take(255).forEach { mention ->
                buffer.writeVariableString(mention)
            }
        }
        message.channel?.let { buffer.writeVariableString(it) }

        return buffer.readByteArray()
    }

    fun decodeFromPayload(data: ByteArray): BitchatMessage? {
        val source = Buffer().write(data)

        try {
            val flags = source.readByte().toUByte()
            val isRelay = (flags and 0x01u) > 0u
            val isPrivate = (flags and 0x02u) > 0u
            val hasOriginalSender = (flags and 0x04u) > 0u
            val hasRecipientNickname = (flags and 0x08u) > 0u
            val hasSenderPeerID = (flags and 0x10u) > 0u
            val hasMentions = (flags and 0x20u) > 0u
            val hasChannel = (flags and 0x40u) > 0u
            val isEncrypted = (flags and 0x80u) > 0u

            val timestamp = Instant.fromEpochMilliseconds(source.readLong())
            val id = source.readVariableString() ?: return null
            val sender = source.readVariableString() ?: return null

            val (content, encryptedContent) = if (isEncrypted) {
                "" to source.readVariableByteArray(isShort = false)
            } else {
                source.readVariableString(isShort = false) to null
            }
            if (content == null && encryptedContent == null) return null

            val originalSender = if (hasOriginalSender) source.readVariableString() else null
            val recipientNickname = if (hasRecipientNickname) source.readVariableString() else null
            val senderPeerId = if (hasSenderPeerID) source.readVariableString() ?: "" else ""

            val mentions = if (hasMentions) {
                val count = source.readByte().toInt() and 0xFF
                List(count) { source.readVariableString() ?: "" }
            } else emptyList()

            val channel = if (hasChannel) source.readVariableString() else null

            return BitchatMessage(
                id = id,
                sender = sender,
                senderPeerId = senderPeerId,
                content = content ?: "",
                timestamp = timestamp,
                channel = channel,
                isPrivate = isPrivate,
                recipientNickname = recipientNickname,
                mentions = mentions,
                isRelay = isRelay,
                originalSender = originalSender,
                isEncrypted = isEncrypted,
                encryptedContent = encryptedContent,
                deliveryStatus = if (isPrivate) DeliveryStatus.Sending else DeliveryStatus.Sent
            )
        } catch (e: Exception) {
            e.stackTraceToString()
            return null
        }
    }
}

private fun Buffer.writeVariableString(value: String, isShort: Boolean = true) {
    val bytes = value.encodeToByteArray()
    if (isShort) {
        val length = minOf(bytes.size, 255)
        this.writeByte(length)
        this.write(bytes, 0, length)
    } else {
        val length = minOf(bytes.size, 65535)
        this.writeShort(length)
        this.write(bytes, 0, length)
    }
}

private fun Buffer.readVariableString(isShort: Boolean = true): String? {
    if (this.exhausted()) return null
    val length =
        if (isShort) this.readByte().toInt() and 0xFF else this.readShort().toInt() and 0xFFFF
    if (this.size < length) return null
    return this.readByteArray(length.toLong()).decodeToString()
}


private fun Buffer.writeVariableByteArray(value: ByteArray, isShort: Boolean = false) {
    if (isShort) {
        val length = minOf(value.size, 255)
        this.writeByte(length)
        this.write(value, 0, length)
    } else {
        val length = minOf(value.size, 65535)
        this.writeShort(length)
        this.write(value, 0, length)
    }
}


private fun Buffer.readVariableByteArray(isShort: Boolean = false): ByteArray? {
    if (this.exhausted()) return null
    val length =
        if (isShort) this.readByte().toInt() and 0xFF else this.readShort().toInt() and 0xFFFF
    if (this.size < length) return null
    return this.readByteArray(length.toLong())
}