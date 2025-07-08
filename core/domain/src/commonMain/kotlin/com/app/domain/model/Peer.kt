package com.app.domain.model

data class Peer(
    val id: String,
    val nickname: String,
    val identityKeyFingerprint: String,
    val rssi: Int? = null,
    val isFavorite: Boolean = false
)