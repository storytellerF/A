package com.storyteller_f.a.panel

interface Platform {
    val name: String
    val usePermanentPanelNavigationDrawer: Boolean get() = false
}

expect fun getPlatform(): Platform
