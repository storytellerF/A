package com.storyteller_f.a.client_lib

import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

expect fun getClient(block: HttpClientConfig<*>.() -> Unit): HttpClient

fun HttpClientConfig<*>.defaultClientConfigure() {
    expectSuccess = true
    install(Auth) {
        custom {
        }
    }
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
        })
    }
    install(Logging) {
        logger = object : Logger {
            override fun log(message: String) {
                Napier.v(tag = "HTTP Client", throwable = null, message = message)
            }
        }
        level = LogLevel.HEADERS
    }
    install(HttpCookies)
    install(WebSockets) {
        pingInterval = 20_000
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
    }
    install(HttpRequestRetry) {
        retryIf { request, response ->
            response.status == HttpStatusCode.Unauthorized && request.headers["cookie"].isNullOrEmpty()
        }
    }
}
