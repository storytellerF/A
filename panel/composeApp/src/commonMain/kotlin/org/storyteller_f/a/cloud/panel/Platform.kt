package org.storyteller_f.a.cloud.panel

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
