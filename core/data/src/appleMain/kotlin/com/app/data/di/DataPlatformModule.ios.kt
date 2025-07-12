package com.app.data.di

import com.app.data.crypto.EncryptionService
import com.app.data.crypto.SecureStorage
import org.koin.core.annotation.Module
import org.koin.core.scope.Scope
import  com.app.data.crypto.KeychainSecureStorage
import org.koin.core.annotation.Single

@Module
actual class DataPlatformModule {
    @Single
    actual fun createSecureStorage(scope: Scope): SecureStorage {
        return KeychainSecureStorage()
    }

    @Single
    actual fun createEncryptionService(scope: Scope): EncryptionService {
        return EncryptionService(scope.get())
    }
}