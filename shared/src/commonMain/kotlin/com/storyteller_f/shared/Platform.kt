package com.storyteller_f.shared

interface Platform {
    val name: String
    val id: String
}

expect fun getPlatform(): Platform
