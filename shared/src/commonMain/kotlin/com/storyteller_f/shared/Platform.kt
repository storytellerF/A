package com.storyteller_f.shared

import io.github.aakira.napier.Antilog

interface Platform {
    val name: String
    val id: String
}

expect fun getPlatform(): Platform

expect val kmpLogger: Antilog
