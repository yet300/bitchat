package com.app.data.crypto

import com.app.data.util.toNSData
import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Foundation.NSData
import platform.Security.*


//TODO IMPLEMENT LATER WITH KOTLIN SWIFT INTEROP
class KeychainSecureStorage : SecureStorage {
    override fun saveData(key: String, data: ByteArray) {
        TODO("Not yet implemented")
    }

    override fun loadData(key: String): ByteArray? {
        TODO("Not yet implemented")
    }

    override fun deleteData(key: String) {
        TODO("Not yet implemented")
    }

    override fun clearAll() {
        TODO("Not yet implemented")
    }

}
