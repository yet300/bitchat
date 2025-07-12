package com.app.data.mapper

import com.app.data.model.BitchatMessageDto
import com.app.domain.model.BitchatMessage


fun BitchatMessage.toDto(): BitchatMessageDto {
    return BitchatMessageDto(
        id = id,
        sender = sender,
        content = content,
        timestamp = timestamp,
        isRelay = isRelay,
        originalSender = originalSender,
        isPrivate = isPrivate,
        recipientNickname = recipientNickname,
        senderPeerId = senderPeerId,
        mentions = mentions,
        channel = channel,
        isEncrypted = isEncrypted,
        encryptedContent = encryptedContent,
    )
}

fun BitchatMessageDto.toDomain(): BitchatMessage {
    return BitchatMessage(
        id = id,
        sender = sender,
        content = content,
        timestamp = timestamp,
        isRelay = isRelay,
        originalSender = originalSender,
        isPrivate = isPrivate,
        recipientNickname = recipientNickname,
        senderPeerId = senderPeerId,
        mentions = mentions,
        channel = channel,
        isEncrypted = isEncrypted,
        encryptedContent = encryptedContent,
    )
}