package com.app.data.di

import com.app.data.crypto.EncryptionService
import com.app.data.crypto.SecureStorage
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.core.scope.Scope

@Module
expect class DataPlatformModule {
    @Single
    fun createSecureStorage(scope: Scope): SecureStorage

    @Single
    fun createEncryptionService(scope: Scope) : EncryptionService
}