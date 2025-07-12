package com.app.data.model

import com.app.data.utils.BinarySerializer
import kotlinx.serialization.Serializable

@Serializable
data class BitchatPacket(
    val version: UByte = 1u,
    val type: UByte,
    val senderId: ByteArray,
    val recipientId: ByteArray? = null,
    val timestamp: ULong,
    val payload: ByteArray,
    val signature: ByteArray? = null,
    var ttl: UByte
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as BitchatPacket

        if (version != other.version) return false
        if (type != other.type) return false
        if (!senderId.contentEquals(other.senderId)) return false
        if (!recipientId.contentEquals(other.recipientId)) return false
        if (timestamp != other.timestamp) return false
        if (!payload.contentEquals(other.payload)) return false
        if (!signature.contentEquals(other.signature)) return false
        if (ttl != other.ttl) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + senderId.contentHashCode()
        result = 31 * result + (recipientId?.contentHashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + (signature?.contentHashCode() ?: 0)
        result = 31 * result + ttl.hashCode()
        return result
    }

    fun toBinaryData(): ByteArray? {
        return BinarySerializer.encode(this)
    }

    companion object {
        fun fromBinaryData(data: ByteArray): BitchatPacket? {
            return BinarySerializer.decode(data)
        }
    }
}