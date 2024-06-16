package com.storyteller_f.a.client_lib

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

actual fun getClient(block: HttpClientConfig<*>.() -> Unit): HttpClient {
    return HttpClient(OkHttp) {
        engine {
            preconfigured = OkHttpClient.Builder()
                .pingInterval(20, TimeUnit.SECONDS)
                .build()
        }
        block()
    }
}
