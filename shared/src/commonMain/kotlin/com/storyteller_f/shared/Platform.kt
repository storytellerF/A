package com.storyteller_f.shared

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
