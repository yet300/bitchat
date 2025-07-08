package com.app.domain.model

import kotlinx.datetime.Instant


data class ReadReceipt(
    val originalMessageId: String,
    val receiptId: String,
    val readerId: String,
    val readerNickname: String,
    val timestamp: Instant
)