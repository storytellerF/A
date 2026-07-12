package com.storyteller_f.a.panel

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
