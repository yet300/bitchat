package com.app.data.mapper

import com.app.data.model.DeliveryAckDto
import com.app.domain.model.DeliveryAck

fun DeliveryAck.toDto(): DeliveryAckDto {
    return DeliveryAckDto(
        originalMessageID = originalMessageId, 
        ackID = ackId,
        recipientID = recipientId,
        recipientNickname = recipientNickname,
        timestamp = timestamp,
        hopCount = hopCount
    )
}

fun DeliveryAckDto.toDomain(): DeliveryAck {
    return DeliveryAck(
        originalMessageId = originalMessageID,
        ackId = ackID,
        recipientId = recipientID,
        recipientNickname = recipientNickname,
        timestamp = timestamp,
        hopCount = hopCount
    )
}