package com.app.data.utils

import com.app.data.model.BitchatPacket
import okio.Buffer
import okio.EOFException
import okio.IOException

//  BinaryProtocol
object BinarySerializer {
    private const val HEADER_SIZE = 13
    private const val SENDER_ID_SIZE = 8
    private const val RECIPIENT_ID_SIZE = 8
    private const val SIGNATURE_SIZE = 64

    private object Flags {
        const val HAS_RECIPIENT: UByte = 0x01u
        const val HAS_SIGNATURE: UByte = 0x02u
        const val IS_COMPRESSED: UByte = 0x04u
    }

    // Encode BitchatPacket to binary format
    fun encode(packet: BitchatPacket): ByteArray {
        val buffer = Buffer()

        // Try to compress payload if beneficial
        var payload = packet.payload
        var originalPayloadSize: UShort? = null
        var isCompressed = false
        if (CompressionUtil.shouldCompress(payload)) {
            CompressionUtil.compress(payload)?.let { compressed ->
                originalPayloadSize = payload.size.toUShort()
                payload = compressed
                isCompressed = true
            }
        }

        buffer.writeByte(packet.version.toInt())
        buffer.writeByte(packet.type.toInt())
        buffer.writeByte(packet.ttl.toInt())
        buffer.writeLong(packet.timestamp.toLong())

        var flags: UByte = 0u
        if (packet.recipientId != null) flags = flags or Flags.HAS_RECIPIENT
        if (packet.signature != null) flags = flags or Flags.HAS_SIGNATURE
        if (isCompressed) flags = flags or Flags.IS_COMPRESSED
        buffer.writeByte(flags.toInt())

        val payloadLength = payload.size + if (isCompressed) 2 else 0
        buffer.writeShort(payloadLength.toShort().toInt())

        // --- Variable sections ---
        // SenderID
        val senderBytes = packet.senderId.take(SENDER_ID_SIZE).toByteArray()
        buffer.write(senderBytes)
        if (senderBytes.size < SENDER_ID_SIZE) {
            buffer.write(ByteArray(SENDER_ID_SIZE - senderBytes.size))
        }

        // RecipientID
        packet.recipientId?.let {
            val recipientBytes = it.take(RECIPIENT_ID_SIZE).toByteArray()
            buffer.write(recipientBytes)
            if (recipientBytes.size < RECIPIENT_ID_SIZE) {
                buffer.write(ByteArray(RECIPIENT_ID_SIZE - recipientBytes.size))
            }
        }

        // Payload
        if (isCompressed && originalPayloadSize != null) {
            buffer.writeShort(originalPayloadSize.toShort().toInt())
        }
        buffer.write(payload)

        // Signature
        packet.signature?.let {
            buffer.write(it.take(SIGNATURE_SIZE).toByteArray())
        }

        return buffer.readByteArray()
    }

    // Decode binary data to BitchatPacket
    fun decode(data: ByteArray): BitchatPacket? {
        if (data.size < HEADER_SIZE + SENDER_ID_SIZE) return null
        val source = Buffer().write(data)

        try {
            // Header
            val version = source.readByte().toUByte()
            if (version != 1u.toUByte()) return null

            val type = source.readByte().toUByte()
            val ttl = source.readByte().toUByte()
            val timestamp = source.readLong().toULong()
            val flags = source.readByte().toUByte()
            val payloadLength = source.readShort().toUShort()

            val hasRecipient = (flags and Flags.HAS_RECIPIENT) > 0u
            val hasSignature = (flags and Flags.HAS_SIGNATURE) > 0u
            val isCompressed = (flags and Flags.IS_COMPRESSED) > 0u

            // SenderID
            val senderId = source.readByteArray(SENDER_ID_SIZE.toLong())

            // RecipientID
            val recipientId =
                if (hasRecipient) source.readByteArray(RECIPIENT_ID_SIZE.toLong()) else null

            // Payload
            val payloadData = if (isCompressed) {
                val originalSize = source.readShort().toUShort().toInt()
                val compressedPayload = source.readByteArray((payloadLength.toInt() - 2).toLong())
                CompressionUtil.decompress(compressedPayload, originalSize)
            } else {
                source.readByteArray(payloadLength.toLong())
            }
            if (payloadData == null) return null

            // Signature
            val signature =
                if (hasSignature) source.readByteArray(SIGNATURE_SIZE.toLong()) else null

            return BitchatPacket(
                version = version,
                type = type,
                ttl = ttl,
                timestamp = timestamp,
                senderId = senderId,
                recipientId = recipientId,
                payload = payloadData,
                signature = signature
            )
        } catch (e: EOFException) {
            return null
        } catch (e: IOException) {
            return null
        }
    }
}