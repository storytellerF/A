package com.storyteller_f.a.client_lib

import com.storyteller_f.shared.finalData
import com.storyteller_f.shared.signature
import io.github.aakira.napier.Napier
import io.ktor.client.plugins.auth.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*

class ClientCustomAuthProvider : AuthProvider {

    override suspend fun addRequestHeaders(request: HttpRequestBuilder, authHeader: HttpAuthHeader?) {
        Napier.v("addRequestHeaders ${authHeader != null}", tag = "ClientAuth")
        if (authHeader is HttpAuthHeader.Single) {
            request.addRequestHeaders(authHeader.blob)
        }
    }

    @Deprecated("Please use sendWithoutRequest function instead", level = DeprecationLevel.ERROR)
    override val sendWithoutRequest: Boolean
        get() = TODO("Not yet implemented")

    override fun sendWithoutRequest(request: HttpRequestBuilder): Boolean {
        return true
    }

    override fun isApplicable(auth: HttpAuthHeader): Boolean {
        Napier.v("isApplicable $auth", tag = "ClientAuth")
        if (auth is HttpAuthHeader.Single) {
            val data = auth.blob
            val localData = LoginViewModel.session?.first
            if (data != localData) {
                LoginViewModel.updateSession(data, null)
            }
        }
        return auth.authScheme == "Custom" && auth is HttpAuthHeader.Single
    }

    override suspend fun refreshToken(response: HttpResponse): Boolean {
        val state = LoginViewModel.state.value as? ClientSession.SignUpSuccess
        val data = LoginViewModel.session?.first
        Napier.v("refreshToken", tag = "ClientAuth")
        return if (state == null || data == null) {
            false
        } else {
            kotlin.runCatching {
                val localPrivateKey = state.privateKey
                LoginViewModel.updateSession(data, signature(localPrivateKey, finalData(data)))
            }.fold({
                Napier.v(tag = "ClientAuth") {
                    "refreshToken Success"
                }
                true
            }, {
                Napier.e("signature failed", it, tag = "ClientAuth")
                false
            })
        }
    }
}

fun HttpRequestBuilder.addRequestHeaders(
    data: String?
) {
    data ?: return
    val state = LoginViewModel.state.value as? ClientSession.SignUpSuccess
    if (state != null) {
        val userInfo = LoginViewModel.user.value
        val localData = LoginViewModel.session?.first
        val localSignature = LoginViewModel.session?.second
        if (userInfo != null && data == localData && localData.isNotBlank() && !localSignature.isNullOrBlank()) {
            val userId = userInfo.id
            if (userInfo.aid.isNullOrBlank()) {
                headers[HttpHeaders.Authorization] =
                    """Custom id="$userId", sig="$localSignature""""
            } else {
                headers[HttpHeaders.Authorization] =
                    """Custom aid="${userInfo.aid}", sig="$localSignature""""
            }
        }
    }
}

class CustomAuthConfig

fun AuthConfig.custom(block: CustomAuthConfig.() -> Unit) {
    CustomAuthConfig().apply(block)
    providers.add(ClientCustomAuthProvider())
}
