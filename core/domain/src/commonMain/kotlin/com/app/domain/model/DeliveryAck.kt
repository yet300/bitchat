package com.app.domain.model

import kotlinx.datetime.Instant

data class DeliveryAck(
    val originalMessageId: String,
    val ackId: String,
    val recipientId: String,
    val recipientNickname: String,
    val timestamp: Instant,
    val hopCount: UByte
)