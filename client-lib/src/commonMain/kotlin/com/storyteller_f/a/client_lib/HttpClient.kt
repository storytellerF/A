package com.storyteller_f.a.client_lib

import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.utils.checkContent
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.callid.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ServerErrorException(val status: HttpStatusCode, val text: String, cause: Exception) : Exception(
    "$status $text",
    cause
)

val globalCookiesStorage = AcceptAllCookiesStorage()

expect fun getClient(block: HttpClientConfig<*>.() -> Unit): HttpClient

@OptIn(ExperimentalUuidApi::class)
fun HttpClientConfig<*>.defaultClientConfigure(cookiesStorage: CookiesStorage = globalCookiesStorage) {
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
    install(CallId) {
        generate { Uuid.random().toString() }
        addToHeader(HttpHeaders.XRequestId)
    }
    install(Logging) {
    }
    install(HttpCookies) {
        storage = cookiesStorage
    }
    install(HttpRequestRetry) {
        retryIf { _, response ->
            response.status == HttpStatusCode.TooManyRequests
        }
        delayMillis {
            0
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

@OptIn(ExperimentalStdlibApi::class)
suspend fun processEncryptedTopic(info: List<TopicInfo>): List<TopicInfo> {
    val value = SignInViewModel.state.value
    val uid = SignInViewModel.user.value?.id
    val key = if (value is ClientSession.SignInSuccess) value.session else null
    return info.map { topicInfo ->
        val content = topicInfo.content
        if (content !is TopicContent.Encrypted || uid == null || key == null) {
            topicInfo
        } else {
            val s = content.encryptedKey[uid]
            topicInfo.copy(
                content = if (s != null) {
                    runCatching {
                        key.decrypt(
                            content.encrypted.hexToByteArray(),
                            s.hexToByteArray()
                        )
                    }.fold(onSuccess = {
                        if (checkContent(it)) {
                            TopicContent.Plain(it)
                        } else {
                            TopicContent.Invalid
                        }
                    }, onFailure = {
                        Napier.e(it) {
                            "decrypt ${topicInfo.id}"
                        }
                        TopicContent.DecryptFailed(it.message.toString())
                    })
                } else {
                    TopicContent.DecryptFailed("auth failed")
                }
            )
        }
    }
}
