package com.storyteller_f.a.cloud.server.common

import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.header

fun ApplicationCall.clientIp(): String {
    val forwarded = request.header("X-Forwarded-For")
        ?.split(",")
        ?.firstOrNull()
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    return forwarded ?: request.origin.remoteAddress
}
