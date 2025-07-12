package com.app.data.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class)
actual fun generateSecureRandomBytes(count: Int): ByteArray {
    if (count <= 0) return ByteArray(0)

    val bytes = ByteArray(count)
    val result = bytes.usePinned { pinned ->
        SecRandomCopyBytes(kSecRandomDefault, count.toULong(), pinned.addressOf(0))
    }

    if (result != 0) {
        return ByteArray(count)
    }

    return bytes
}