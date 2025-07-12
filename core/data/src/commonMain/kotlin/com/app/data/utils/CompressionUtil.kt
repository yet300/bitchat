package com.app.data.utils

object CompressionUtil {
    private const val COMPRESSION_THRESHOLD = 100  // bytes
    
    fun shouldCompress(data: ByteArray): Boolean {
        // Temporarily disabled compression
        return false
    }
    
    fun compress(data: ByteArray): ByteArray? {
        // Temporarily disabled compression
        return null
    }
    
    fun decompress(compressedData: ByteArray, originalSize: Int): ByteArray? {
        // Temporarily disabled compression
        return null
    }
}