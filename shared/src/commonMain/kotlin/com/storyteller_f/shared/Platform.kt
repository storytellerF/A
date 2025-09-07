package com.storyteller_f.shared

import io.github.aakira.napier.Antilog
import kotlinx.serialization.json.Json

interface Platform {
    val name: String
    val id: String
}

expect fun getPlatform(): Platform

expect val kmpLogger: Antilog

val commonJson = Json { ignoreUnknownKeys = true }
