package com.storyteller_f.shared

class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override val id: String
        get() = ""
}

actual fun getPlatform(): Platform = JVMPlatform()