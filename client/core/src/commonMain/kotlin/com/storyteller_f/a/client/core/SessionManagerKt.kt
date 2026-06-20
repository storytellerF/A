@file:Suppress("detekt.formatting")

package com.storyteller_f.a.client.core

import com.storyteller_f.a.api.SignInBody
import com.storyteller_f.a.api.SignInResponse
import com.storyteller_f.a.api.SignUpBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import com.storyteller_f.shared.model.PanelAccountInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.utils.mapResult

context(c: CoroutineScope)
fun SimpleUserSessionManager.startBackgroundTask(): List<Job> {
    val webSocketConnector = c.launch {
        webSocketClient.connectWebSocket()
    }
    return listOf(webSocketConnector)
}

context(c: CoroutineScope)
suspend inline fun <R> SimpleUserSessionManager.onBackgroundTask(block: UserSessionManager.() -> R): R {
    val jobs = startBackgroundTask()
    try {
        return block()
    } finally {
        jobs.forEach {
            it.cancelAndJoin()
        }
    }
}

suspend fun UserSessionManager.getUserSignInPass(
    authKey: AuthKey
): SignResult<UserInfo> {
    return prepareSignInFromPrivateKey(authKey) {
        getData()
    }.mapResult { param ->
        signIn(SignInBody(param.address, param.signature)).map { signIn ->
            when (signIn) {
                is SignInResponse.Success -> {
                    SignResult(
                        signIn.userInfo,
                        param.data,
                        param.signature,
                        param.address,
                        param.authKey
                    )
                }

                SignInResponse.RequiresTotp -> error("totp required")
            }
        }
    }.getOrThrow()
}

suspend fun UserSessionManager.getUserSignUpPass(
    authKey: AuthKey
): SignResult<UserInfo> {
    return prepareSignInFromPrivateKey(authKey) {
        getData()
    }.mapResult { param ->
        val encryptionPublicKey = (param.authKey as? AuthKey.Dilithium)?.derEncryptionPublicKey
        signUp(
            SignUpBody(
                param.authKey.derPublicKey,
                param.signature,
                encryptionPublicKey
            )
        ).map { userInfo ->

            SignResult(
                userInfo,
                param.data,
                param.signature,
                param.address,
                param.authKey
            )
        }
    }.getOrThrow()
}

suspend fun PanelSessionManager.getPanelUserSignUpPass(
    authKey: AuthKey
): SignResult<PanelAccountInfo> {
    return prepareSignInFromPrivateKey(authKey, {
        getData()
    }).mapResult { param ->
        val encryptionPublicKey1 = (param.authKey as? AuthKey.Dilithium)?.derEncryptionPublicKey
        signUp(
            SignUpBody(
                param.authKey.derPublicKey,
                param.signature,
                encryptionPublicKey1
            )
        ).map {
            SignResult(
                it,
                param.data,
                param.signature,
                param.address,
                authKey
            )
        }
    }.getOrThrow()
}

suspend fun PanelSessionManager.getPanelUserSignInPass(
    authKey: AuthKey
): SignResult<PanelAccountInfo> {
    return prepareSignInFromPrivateKey(authKey, {
        getData()
    }).mapResult { param ->
        signIn(SignInBody(param.address, param.signature)).map {
            SignResult(
                it,
                 param.data,
                param.signature,
                param.address,
                authKey
            )
        }
    }.getOrThrow()
}
