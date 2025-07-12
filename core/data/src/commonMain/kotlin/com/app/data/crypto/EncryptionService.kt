package com.app.data.crypto


import com.app.data.utils.generateSecureRandomBytes

expect class EncryptionService(secureStorage: SecureStorage) {

    suspend fun initialize()

    fun getCombinedPublicKeyData(): ByteArray

    suspend fun addPeerPublicKey(peerID: String, publicKeyData: ByteArray)

    fun getPeerIdentityKey(peerID: String): ByteArray?


    suspend fun clearPersistentIdentity()


    suspend fun encrypt(data: ByteArray, forPeerID: String): ByteArray


    suspend fun decrypt(data: ByteArray, fromPeerID: String): ByteArray


    suspend fun sign(data: ByteArray): ByteArray


    suspend fun verify(signature: ByteArray, data: ByteArray, fromPeerID: String): Boolean
}

object MessagePadding {
    private val blockSizes = listOf(256, 512, 1024, 2048)

    fun pad(data: ByteArray, targetSize: Int): ByteArray {
        if (data.size >= targetSize) return data
        val paddingNeeded = targetSize - data.size
        if (paddingNeeded > 255) return data

        val padded = ByteArray(targetSize)
        data.copyInto(padded, 0, 0, data.size)

        val randomBytes = generateSecureRandomBytes(paddingNeeded - 1)
        randomBytes.copyInto(padded, data.size, 0, randomBytes.size)

        padded[targetSize - 1] = paddingNeeded.toByte()
        return padded
    }

    fun unpad(data: ByteArray): ByteArray {
        if (data.isEmpty()) return data
        val paddingLength = data.last().toInt() and 0xFF
        if (paddingLength <= 0 || paddingLength > data.size) return data
        return data.copyOfRange(0, data.size - paddingLength)
    }

    fun optimalBlockSize(dataSize: Int): Int {
        val totalSize = dataSize + 16
        return blockSizes.firstOrNull { totalSize <= it } ?: dataSize
    }
}