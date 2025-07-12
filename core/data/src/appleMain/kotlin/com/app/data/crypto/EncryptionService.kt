package com.app.data.crypto




//TODO IMPLEMENT LATER WITH KOTLIN SWIFT INTEROP
actual class EncryptionService actual constructor(
    private val secureStorage: SecureStorage
) {
    actual suspend fun initialize() {
    }

    actual fun getCombinedPublicKeyData(): ByteArray {
        TODO("Not yet implemented")
    }

    actual suspend fun addPeerPublicKey(peerID: String, publicKeyData: ByteArray) {
    }

    actual fun getPeerIdentityKey(peerID: String): ByteArray? {
        TODO("Not yet implemented")
    }

    actual suspend fun clearPersistentIdentity() {
    }

    actual suspend fun encrypt(data: ByteArray, forPeerID: String): ByteArray {
        TODO("Not yet implemented")
    }

    actual suspend fun decrypt(data: ByteArray, fromPeerID: String): ByteArray {
        TODO("Not yet implemented")
    }

    actual suspend fun sign(data: ByteArray): ByteArray {
        TODO("Not yet implemented")
    }

    actual suspend fun verify(signature: ByteArray, data: ByteArray, fromPeerID: String): Boolean {
        TODO("Not yet implemented")
    }

}