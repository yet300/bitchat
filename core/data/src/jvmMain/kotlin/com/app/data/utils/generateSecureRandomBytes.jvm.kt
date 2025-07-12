package com.app.data.utils

import java.security.SecureRandom

actual fun generateSecureRandomBytes(count: Int): ByteArray {
    return ByteArray(count).apply {
        SecureRandom().nextBytes(this)
    }
}