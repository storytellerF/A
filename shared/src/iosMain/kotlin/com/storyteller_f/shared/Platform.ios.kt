package com.storyteller_f.shared

import platform.UIKit.UIDevice

class IOSPlatform : Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
    override val id: String
        get() = ""
}

actual fun getPlatform(): Platform = IOSPlatform()
