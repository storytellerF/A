package com.storyteller_f.a.client_lib

import io.ktor.client.*

actual fun getClient(block: HttpClientConfig<*>.() -> Unit): HttpClient {
    return HttpClient {
        block()
    }
}
