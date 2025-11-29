package com.storyteller_f.a.client.core

import com.storyteller_f.shared.finalData
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.callid.CallId
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.buildUrl
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ServerErrorException(val status: HttpStatusCode, val text: String, cause: Exception) :
    Exception("$status, $text", cause)

expect fun getClient(block: HttpClientConfig<*>.() -> Unit): HttpClient

@OptIn(ExperimentalUuidApi::class)
fun HttpClientConfig<*>.defaultClientConfigure(
    cookiesStorage: CookiesStorage,
    manager: UserSessionModel,
    httpUrl: String? = null,
    logLevel: LogLevel = LogLevel.HEADERS,
) {
    expectSuccess = true
    install(Auth) {
        custom {
            configClientAuth(manager) { u, l ->
                addRequestHeadersFromInfo(u, l)
            }
        }
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 15.minutes.inWholeMilliseconds
    }
    defaultRequest {
        header("a-ts", manager.generateData())
        if (httpUrl != null) {
            url(httpUrl)
        }
    }
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
        })
    }
    install(CallId) {
        generate { Uuid.random().toString() }
        addToHeader(HttpHeaders.XRequestId)
    }
    install(Logging) {
        level = logLevel
    }
    install(HttpCookies) {
        storage = cookiesStorage
    }
    install(HttpRequestRetry) {
        retryIf { _, response ->
            response.status == HttpStatusCode.TooManyRequests
        }
        delayMillis {
            1000
        }
    }
    install(WebSockets) {
        pingInterval = 2.seconds
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
    }
    HttpResponseValidator {
        handleResponseExceptionWithRequest { exception, _ ->
            if (exception is ResponseException) {
                val exceptionResponse = exception.response
                val exceptionResponseText = exceptionResponse.bodyAsText()
                throw ServerErrorException(exceptionResponse.status, exceptionResponseText, exception)
            }
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
fun HttpClientConfig<*>.defaultClientConfigureForPanel(
    cookiesStorage: CookiesStorage,
    manager: PanelSessionModel,
    httpUrl: String? = null,
    logLevel: LogLevel = LogLevel.HEADERS,
) {
    expectSuccess = true
    install(Auth) {
        custom {
            configClientAuth(manager) { u, l ->
                addRequestHeadersFromInfo(u, l)
            }
        }
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 15.minutes.inWholeMilliseconds
    }
    defaultRequest {
        header("a-ts", manager.generateData())
        if (httpUrl != null) {
            url(httpUrl)
        }
    }
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
        })
    }
    install(CallId) {
        generate { Uuid.random().toString() }
        addToHeader(HttpHeaders.XRequestId)
    }
    install(Logging) {
        level = logLevel
    }
    install(HttpCookies) {
        storage = cookiesStorage
    }
    install(HttpRequestRetry) {
        retryIf { _, response ->
            response.status == HttpStatusCode.TooManyRequests
        }
        delayMillis {
            1000
        }
    }
    HttpResponseValidator {
        handleResponseExceptionWithRequest { exception, _ ->
            if (exception is ResponseException) {
                val exceptionResponse = exception.response
                val exceptionResponseText = exceptionResponse.bodyAsText()
                throw ServerErrorException(exceptionResponse.status, exceptionResponseText, exception)
            }
        }
    }
}

private fun <U> CustomClientAuthProvider.CustomAuthConfig.configClientAuth(
    manager: SessionModel<U>,
    addRequestHeader: HttpRequestBuilder.(U, String) -> Unit
) {
    addRequestHeaders { data, request ->
        Napier.v("addRequestHeaders data: $data url: ${request.url}", tag = "ClientAuth")
        if (data == manager.dataAndSignature?.first) {
            request.addRequestHeaders(manager, addRequestHeader)
        }
    }
    updateDataIfNeed { data ->
        val localData = manager.dataAndSignature?.first
        Napier.v("updateDataIfNeed new: $data old: $localData", tag = "ClientAuth")
        if (data != localData) {
            manager.updateSignature(data, null)
        }
    }
    refreshSignature {
        val session = manager.currentUserPass
        val data = manager.dataAndSignature?.first
        Napier.v("refreshSignature $data", tag = "ClientAuth")
        if (session == null || data == null) {
            false
        } else {
            runCatching {
                manager.updateSignature(data, session.signature(finalData(data)).getOrThrow())
            }.fold({
                Napier.v(tag = "ClientAuth") {
                    "refreshSignature success"
                }
                true
            }, {
                Napier.e("refreshSignature failed", it, tag = "ClientAuth")
                false
            })
        }
    }
}

fun buildWebSocketUrl(wsServerUrl: String): String = buildUrl {
    takeFrom(wsServerUrl)
    appendPathSegments("link")
}.toString()
