package com.storyteller_f.shared

import io.github.aakira.napier.Antilog
import io.github.aakira.napier.Napier
import kotlinx.serialization.json.Json

interface Platform {
    val name: String
    val id: String
}

expect fun getPlatform(): Platform

expect val kmpLogger: Antilog

fun setupKmpLogger() {
    Napier.takeLogarithm()
    Napier.base(kmpLogger)
}

val commonJson = Json { ignoreUnknownKeys = true }
