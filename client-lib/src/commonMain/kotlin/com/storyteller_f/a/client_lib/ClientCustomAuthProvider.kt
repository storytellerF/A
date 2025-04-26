package com.storyteller_f.a.client_lib

import com.storyteller_f.shared.finalData
import com.storyteller_f.shared.model.UserInfo
import io.github.aakira.napier.Napier
import io.ktor.client.plugins.auth.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*

class ClientCustomAuthProvider : AuthProvider {

    override suspend fun addRequestHeaders(request: HttpRequestBuilder, authHeader: HttpAuthHeader?) {
        Napier.v("addRequestHeaders ${authHeader != null} ${request.url}", tag = "ClientAuth")
        if (authHeader is HttpAuthHeader.Single) {
            if (authHeader.blob == SignInViewModel.session?.first) {
                request.addRequestHeaders()
            }
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
        val localData = SignInViewModel.session?.first
        Napier.v("isApplicable $auth $localData", tag = "ClientAuth")
        if (auth.authScheme == "Custom" && auth is HttpAuthHeader.Single) {
            val data = auth.blob
            if (data != localData) {
                SignInViewModel.updateSession(data, null)
            }
            return true
        }
        return false
    }

    override suspend fun refreshToken(response: HttpResponse): Boolean {
        val state = SignInViewModel.state.value as? ClientSession.SignInSuccess
        val data = SignInViewModel.session?.first
        Napier.v("refreshToken $data ${state != null}", tag = "ClientAuth")
        return if (state == null || data == null) {
            false
        } else {
            runCatching {
                SignInViewModel.updateSession(data, state.session.signature(finalData(data)).getOrThrow())
            }.fold({
                Napier.v(tag = "ClientAuth") {
                    "refreshToken success"
                }
                true
            }, {
                Napier.e("refreshToken failed", it, tag = "ClientAuth")
                false
            })
        }
    }
}

suspend fun HttpRequestBuilder.addRequestHeaders() {
    val state = SignInViewModel.state.value as? ClientSession.SignInSuccess
    val session = SignInViewModel.session
    val userInfo = SignInViewModel.user.value
    Napier.i(tag = "ClientAuth") {
        "addRequestHeaders $state $session"
    }
    if (state != null && session != null) {
        val (localData, localSignature) = session
        if (localData.isNotBlank() && !localSignature.isNullOrBlank()) {
            Napier.i {
                "addRequestHeaders headers $localData $localSignature"
            }
            if (userInfo == null) {
                headers[HttpHeaders.Authorization] = """Custom ad="${state.session.address()}", sig="$localSignature""""
            } else {
                addRequestHeaders(userInfo, localSignature)
            }
        }
    }
}

fun HttpRequestBuilder.addRequestHeaders(userInfo: UserInfo, sig: String) {
    if (userInfo.aid.isNullOrBlank()) {
        val userId = userInfo.id
        headers[HttpHeaders.Authorization] =
            """Custom id="$userId", sig="$sig""""
    } else {
        headers[HttpHeaders.Authorization] =
            """Custom aid="${userInfo.aid}", sig="$sig""""
    }
}

class CustomAuthConfig

fun AuthConfig.custom(block: CustomAuthConfig.() -> Unit) {
    CustomAuthConfig().apply(block)
    providers.add(ClientCustomAuthProvider())
}
