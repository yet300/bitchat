package com.yet.bitchat

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform