package com.storyteller_f.a.client_lib

import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

actual fun getClient(block: HttpClientConfig<*>.() -> Unit): HttpClient {
    return HttpClient(Java) {
        install(ContentNegotiation) {
            json()
        }
        block()
    }
}