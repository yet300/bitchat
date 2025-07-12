package com.app.data.crypto

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.util.Base64
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec


class JvmSecureStorage : SecureStorage {

    private companion object {
        const val KEYSTORE_TYPE = "PKCS12"
        const val KEYSTORE_FILE_NAME = "bitchat.keystore.p12"
        const val MASTER_KEY_ALIAS = "bitchat-master-key"

        const val PROPERTIES_FILE_NAME = "bitchat.secure.properties"

        const val ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding"
        const val AES_KEY_SIZE = 256
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH = 128
    }

    private val storageDir = File(System.getProperty("user.home"), ".bitchat").apply { mkdirs() }
    private val keyStoreFile = File(storageDir, KEYSTORE_FILE_NAME)


    private val keyStorePassword = "bitchat-ks-password".toCharArray()

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_TYPE)
    private val masterKey: SecretKey

    private val properties = Properties()
    private val propertiesFile = File(storageDir, PROPERTIES_FILE_NAME)

    init {
        if (keyStoreFile.exists()) {
            FileInputStream(keyStoreFile).use { keyStore.load(it, keyStorePassword) }
        } else {
            keyStore.load(null, keyStorePassword)
        }

        masterKey = if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            val protection = KeyStore.PasswordProtection(keyStorePassword)
            val entry = keyStore.getEntry(MASTER_KEY_ALIAS, protection) as KeyStore.SecretKeyEntry
            entry.secretKey
        } else {
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(AES_KEY_SIZE)
            val newKey = keyGenerator.generateKey()
            val entry = KeyStore.SecretKeyEntry(newKey)
            val protection = KeyStore.PasswordProtection(keyStorePassword)
            keyStore.setEntry(MASTER_KEY_ALIAS, entry, protection)
            saveKeyStore()
            newKey
        }

        if (propertiesFile.exists()) {
            FileInputStream(propertiesFile).use { properties.load(it) }
        }
    }

    private fun saveKeyStore() {
        FileOutputStream(keyStoreFile).use { keyStore.store(it, keyStorePassword) }
    }

    private fun saveProperties() {
        FileOutputStream(propertiesFile).use { properties.store(it, null) }
    }

    override fun saveData(key: String, data: ByteArray) {
        val encryptedData = encrypt(data)
        val base64String = Base64.getEncoder().encodeToString(encryptedData)
        properties.setProperty(key, base64String)
        saveProperties()
    }

    override fun loadData(key: String): ByteArray? {
        val base64String = properties.getProperty(key) ?: return null
        return try {
            val encryptedData = Base64.getDecoder().decode(base64String)
            decrypt(encryptedData)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun deleteData(key: String) {
        properties.remove(key)
        saveProperties()
    }

    override fun clearAll() {
        properties.clear()
        saveProperties()
    }


    private fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(data)
        return iv + ciphertext
    }

    private fun decrypt(encryptedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, encryptedData, 0, GCM_IV_LENGTH)

        val ciphertext = encryptedData.copyOfRange(GCM_IV_LENGTH, encryptedData.size)
        cipher.init(Cipher.DECRYPT_MODE, masterKey, gcmSpec)
        return cipher.doFinal(ciphertext)
    }
}