package com.app.data.crypto

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

fun ByteArray.encodeBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
fun String.decodeBase64String(): ByteArray = Base64.decode(this, Base64.NO_WRAP)


class AndroidSecureStorage(context: Context) : SecureStorage {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val sharedPreferences = EncryptedSharedPreferences.create(
        "bitchat_secure_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun saveData(key: String, data: ByteArray) {
        sharedPreferences.edit().putString(key, data.encodeBase64()).apply()
    }

    override fun loadData(key: String): ByteArray? {
        val base64Data = sharedPreferences.getString(key, null)
        return base64Data?.decodeBase64String()
    }

    override fun deleteData(key: String) {
        sharedPreferences.edit().remove(key).apply()
    }

    override fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }
}