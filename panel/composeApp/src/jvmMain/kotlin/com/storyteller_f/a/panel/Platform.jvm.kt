package com.storyteller_f.a.panel

class JVMPlatform : Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override val usePermanentPanelNavigationDrawer: Boolean = true
}

actual fun getPlatform(): Platform = JVMPlatform()
