package com.app.data.mapper

import com.app.data.model.ReadReceiptDto
import com.app.domain.model.ReadReceipt

fun ReadReceipt.toDto(): ReadReceiptDto {
    return ReadReceiptDto(
        originalMessageID = originalMessageId,
        receiptID = receiptId,
        readerID = readerId,
        readerNickname = readerNickname,
        timestamp = timestamp
    )
}

fun ReadReceiptDto.toDomain(): ReadReceipt {
    return ReadReceipt(
        originalMessageId = originalMessageID,
        receiptId = receiptID,
        readerId = readerID,
        readerNickname = readerNickname,
        timestamp = timestamp
    )
}