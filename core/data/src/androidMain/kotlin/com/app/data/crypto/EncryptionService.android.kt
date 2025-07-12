package com.app.data.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec


actual class EncryptionService actual constructor(
    private val secureStorage: SecureStorage
) {

    private lateinit var privateKey: X25519PrivateKeyParameters
    private lateinit var publicKey: X25519PublicKeyParameters
    private lateinit var signingPrivateKey: Ed25519PrivateKeyParameters
    private lateinit var signingPublicKey: Ed25519PublicKeyParameters
    private lateinit var identityKey: Ed25519PrivateKeyParameters
    private lateinit var identityPublicKey: Ed25519PublicKeyParameters

    // Storage for peer keys
    private val peerPublicKeys = mutableMapOf<String, X25519PublicKeyParameters>()
    private val peerSigningKeys = mutableMapOf<String, Ed25519PublicKeyParameters>()
    private val peerIdentityKeys = mutableMapOf<String, Ed25519PublicKeyParameters>()
    private val sharedSecrets = mutableMapOf<String, ByteArray>()

    private val secureRandom = SecureRandom()

    actual suspend fun initialize() = withContext(Dispatchers.IO) {
        val x25519Generator = X25519KeyPairGenerator()
        x25519Generator.init(X25519KeyGenerationParameters(secureRandom))
        val x25519KeyPair = x25519Generator.generateKeyPair()
        privateKey = x25519KeyPair.private as X25519PrivateKeyParameters
        publicKey = x25519KeyPair.public as X25519PublicKeyParameters

        val ed25519Generator = Ed25519KeyPairGenerator()
        ed25519Generator.init(Ed25519KeyGenerationParameters(secureRandom))
        val ed25519KeyPair = ed25519Generator.generateKeyPair()
        signingPrivateKey = ed25519KeyPair.private as Ed25519PrivateKeyParameters
        signingPublicKey = ed25519KeyPair.public as Ed25519PublicKeyParameters


        val identityKeyBytes = secureStorage.loadData("identity_key")
        if (identityKeyBytes != null) {
            identityKey = Ed25519PrivateKeyParameters(identityKeyBytes, 0)
        } else {
            val newIdentityKeyPair =
                Ed25519KeyPairGenerator().apply { init(Ed25519KeyGenerationParameters(secureRandom)) }
                    .generateKeyPair()
            identityKey = newIdentityKeyPair.private as Ed25519PrivateKeyParameters
            secureStorage.saveData("identity_key", identityKey.encoded)
        }
        identityPublicKey = identityKey.generatePublicKey()

        identityPublicKey = Ed25519PublicKeyParameters(identityKey.encoded, 0)
    }

    actual fun getCombinedPublicKeyData(): ByteArray {
        val combined = ByteArray(96)
        System.arraycopy(publicKey.encoded, 0, combined, 0, 32)  // X25519 key
        System.arraycopy(signingPublicKey.encoded, 0, combined, 32, 32)  // Ed25519 signing key
        System.arraycopy(identityPublicKey.encoded, 0, combined, 64, 32)  // Ed25519 identity key
        return combined
    }

    actual suspend fun addPeerPublicKey(peerID: String, publicKeyData: ByteArray) {
        if (publicKeyData.size != 96) {
            throw Exception("Invalid public key data size: ${publicKeyData.size}, expected 96")
        }

        // Extract all three keys: 32 for key agreement + 32 for signing + 32 for identity
        val keyAgreementData = publicKeyData.sliceArray(0..31)
        val signingKeyData = publicKeyData.sliceArray(32..63)
        val identityKeyData = publicKeyData.sliceArray(64..95)

        val peerPublicKey = X25519PublicKeyParameters(keyAgreementData, 0)
        peerPublicKeys[peerID] = peerPublicKey

        val peerSigningKey = Ed25519PublicKeyParameters(signingKeyData, 0)
        peerSigningKeys[peerID] = peerSigningKey

        val peerIdentityKey = Ed25519PublicKeyParameters(identityKeyData, 0)
        peerIdentityKeys[peerID] = peerIdentityKey

        // Generate shared secret for encryption using X25519
        val agreement = X25519Agreement()
        agreement.init(privateKey)
        val sharedSecret = ByteArray(32)
        agreement.calculateAgreement(peerPublicKey, sharedSecret, 0)

        // Derive symmetric key using HKDF with same salt as iOS
        val salt = "bitchat-v1".toByteArray()
        val derivedKey = hkdf(sharedSecret, salt, byteArrayOf(), 32)
        sharedSecrets[peerID] = derivedKey
    }

    actual fun getPeerIdentityKey(peerID: String): ByteArray? {
        return peerIdentityKeys[peerID]?.encoded
    }

    actual suspend fun clearPersistentIdentity() {
        secureStorage.deleteData("identity_key")
    }

    actual suspend fun encrypt(data: ByteArray, forPeerID: String): ByteArray {
        val symmetricKey = sharedSecrets[forPeerID]
            ?: throw Exception("No shared secret for peer $forPeerID")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(symmetricKey, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)

        val iv = cipher.iv
        val ciphertext = cipher.doFinal(data)

        // Combine IV and ciphertext (same format as iOS AES.GCM.SealedBox.combined)
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

        return combined
    }

    actual suspend fun decrypt(
        data: ByteArray,
        fromPeerID: String
    ): ByteArray {
        val symmetricKey = sharedSecrets[fromPeerID]
            ?: throw Exception("No shared secret for peer $fromPeerID")

        if (data.size < 16) { // 12 bytes IV + 16 bytes tag minimum for GCM
            throw Exception("Invalid encrypted data size")
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(symmetricKey, "AES")

        // Extract IV and ciphertext
        val iv = data.sliceArray(0..11)  // GCM IV is 12 bytes
        val ciphertext = data.sliceArray(12 until data.size)

        val gcmSpec = GCMParameterSpec(128, iv)  // 128-bit authentication tag
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        return cipher.doFinal(ciphertext)
    }

    actual suspend fun sign(data: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, signingPrivateKey)
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    actual suspend fun verify(
        signature: ByteArray,
        data: ByteArray,
        fromPeerID: String
    ): Boolean {
        val verifyingKey = peerSigningKeys[fromPeerID]
            ?: throw Exception("No signing key for peer $fromPeerID")

        val signer = Ed25519Signer()
        signer.init(false, verifyingKey)
        signer.update(data, 0, data.size)
        return signer.verifySignature(signature)
    }

    private fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        // Extract
        val hmac = javax.crypto.Mac.getInstance("HmacSHA256")
        val saltKey = SecretKeySpec(if (salt.isEmpty()) ByteArray(32) else salt, "HmacSHA256")
        hmac.init(saltKey)
        val prk = hmac.doFinal(ikm)

        // Expand
        hmac.init(SecretKeySpec(prk, "HmacSHA256"))
        val result = ByteArray(length)
        var offset = 0
        var counter = 1

        while (offset < length) {
            hmac.reset()
            if (counter > 1) {
                hmac.update(result, offset - 32, 32)
            }
            hmac.update(info)
            hmac.update(counter.toByte())

            val t = hmac.doFinal()
            val remaining = length - offset
            val toCopy = minOf(t.size, remaining)
            System.arraycopy(t, 0, result, offset, toCopy)

            offset += toCopy
            counter++
        }

        return result
    }
}