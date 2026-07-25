package com.storyteller_f.a.client.compose_core.common

import androidx.compose.runtime.compositionLocalOf
import io.ktor.client.HttpClient

val LocalClient = compositionLocalOf<HttpClient> {
    error("no client")
}
