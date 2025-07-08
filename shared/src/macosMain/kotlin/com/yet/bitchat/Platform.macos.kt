package com.yet.bitchat

import platform.Foundation.NSProcessInfo

class MACPlatform: Platform {
    override val name: String = NSProcessInfo.processInfo.operatingSystemVersionString
}

actual fun getPlatform(): Platform = MACPlatform()