package com.app.domain.model

data class Channel(
    val name: String,
    val isJoined: Boolean,
    val isPasswordProtected: Boolean,
    val unreadCount: Int = 0,
    val creatorPeerId: String? = null,
    val isRetentionEnabled: Boolean = false
)