package com.storyteller_f.a.client.core

import com.storyteller_f.shared.model.PanelAccountInfo
import com.storyteller_f.shared.model.UserInfo
import io.github.aakira.napier.Napier
import io.ktor.client.plugins.auth.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*

class CustomClientAuthProvider(val config: CustomAuthConfig) : AuthProvider {

    class CustomAuthConfig {
        lateinit var addRequestHeaders: suspend (String, HttpRequestBuilder) -> Unit
        lateinit var updateDataIfNeed: (String) -> Unit
        lateinit var refreshSignature: suspend (HttpResponse) -> Boolean

        fun addRequestHeaders(block: suspend (String, HttpRequestBuilder) -> Unit) {
            addRequestHeaders = block
        }

        fun updateDataIfNeed(block: (String) -> Unit) {
            updateDataIfNeed = block
        }

        fun refreshSignature(block: suspend (HttpResponse) -> Boolean) {
            refreshSignature = block
        }
    }

    override suspend fun addRequestHeaders(
        request: HttpRequestBuilder,
        authHeader: HttpAuthHeader?
    ) {
        if (authHeader is HttpAuthHeader.Single) {
            config.addRequestHeaders(authHeader.blob, request)
        }
    }

    @Deprecated(
        "Please use sendWithoutRequest function instead",
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("TODO(\"Not yet implemented\")")
    )
    override val sendWithoutRequest: Boolean
        get() = TODO("Not yet implemented")

    override fun sendWithoutRequest(request: HttpRequestBuilder): Boolean {
        Napier.v("sendWithoutRequest ${request.url}", tag = "ClientAuth")
        return true
    }

    override fun isApplicable(auth: HttpAuthHeader): Boolean {
        if (auth.authScheme == "Custom" && auth is HttpAuthHeader.Single) {
            config.updateDataIfNeed(auth.blob)
            return true
        }
        return false
    }

    override suspend fun refreshToken(response: HttpResponse): Boolean {
        return config.refreshSignature(response)
    }
}

suspend fun <U> HttpRequestBuilder.addRequestHeaders(
    sessionModel: SessionModel<U>,
    addRequestHeader: HttpRequestBuilder.(U, String) -> Unit
) {
    val passSession = sessionModel.currentUserPass
    val session = sessionModel.dataAndSignature
    val userInfo = sessionModel.userHandler.data.value
    Napier.i(tag = "ClientAuth") {
        "addRequestHeaders session: $session"
    }
    if (session != null && passSession != null) {
        val (localData, localSignature) = session
        if (localData.isNotBlank() && !localSignature.isNullOrBlank()) {
            Napier.i {
                "addRequestHeaders headers $localData $localSignature"
            }
            if (userInfo == null) {
                val address = passSession.address().getOrThrow()
                headers[HttpHeaders.Authorization] =
                    """Custom ad="$address", sig="$localSignature""""
            } else {
                addRequestHeader(userInfo, localSignature)
            }
        }
    }
}

fun HttpRequestBuilder.addRequestHeadersFromInfo(userInfo: UserInfo, sig: String) {
    if (userInfo.aid.isNullOrBlank()) {
        val userId = userInfo.id
        headers[HttpHeaders.Authorization] =
            """Custom id="$userId", sig="$sig""""
    } else {
        headers[HttpHeaders.Authorization] =
            """Custom aid="${userInfo.aid}", sig="$sig""""
    }
}

fun HttpRequestBuilder.addRequestHeadersFromInfo(userInfo: PanelAccountInfo, sig: String) {
    val userId = userInfo.id
    headers[HttpHeaders.Authorization] =
        """Custom id="$userId", sig="$sig""""
}

fun AuthConfig.custom(block: CustomClientAuthProvider.CustomAuthConfig.() -> Unit) {
    providers.add(
        CustomClientAuthProvider(CustomClientAuthProvider.CustomAuthConfig().apply(block))
    )
}
