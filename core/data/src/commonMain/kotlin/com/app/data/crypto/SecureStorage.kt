package com.app.data.crypto

interface SecureStorage {
    fun saveData(key: String, data: ByteArray)
    fun loadData(key: String): ByteArray?
    fun deleteData(key: String)

    fun clearAll()

}