@file:Suppress("detekt.formatting")

package com.storyteller_f.a.client.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.storyteller_f.shared.model.PanelAccountInfo
import com.storyteller_f.shared.model.UserInfo

context(c: CoroutineScope)
fun UserSessionManager.startBackgroundTask(): List<Job> {
    return listOf(c.launch {
        val model = model
        combine(model.state, model.userHandler.data) { t1, t2 ->
            t1 to t2
        }.distinctUntilChanged().collect { (state, userInfo) ->
            if (state is ClientSessionState.Success && userInfo == null) {
                login()
            }
        }
    }, c.launch {
        model.state.collect {
            updateAddress(it)
        }
    }, c.launch {
        webSocketClient.connectWebSocket()
    })
}

context(c: CoroutineScope)
suspend inline fun <R> UserSessionManager.startBackgroundTask(block: UserSessionManager.() -> R): R {
    val jobs = startBackgroundTask()
    val result = block()
    jobs.forEach {
        it.cancelAndJoin()
    }
    return result
}

context(c: CoroutineScope)
fun PanelSessionManager.startBackgroundTask(): List<Job> {
    return listOf(c.launch {
        val model = model
        combine(model.state, model.userHandler.data) { t1, t2 ->
            t1 to t2
        }.distinctUntilChanged().collect { (state, userInfo) ->
            if (state is ClientSessionState.Success && userInfo == null) {
                login()
            }
        }
    }, c.launch {
        model.state.collect {
            updateAddress(it)
        }
    })
}

context(c: CoroutineScope)
suspend inline fun <R> PanelSessionManager.startBackgroundTask(block: PanelSessionManager.() -> R): R {
    val jobs = startBackgroundTask()
    val result = block()
    jobs.forEach {
        it.cancelAndJoin()
    }
    return result
}

fun <U> getRawUserPassInfoFromAuthKey(
    param: BuildPassParam<U>
): RawUserPassInfo {
    if (param.authKey is AuthKey.Dilithium) {
        return RawUserPassInfo(
            param.address,
            param.authKey,
        )
    }
    return RawUserPassInfo(
        param.address,
        param.authKey,
    )
}

suspend fun UserSessionManager.getUserPass(
    authKey: AuthKey,
    isSignUp: Boolean,
    buildUserPass: suspend (RawUserPassInfo) -> UserPass
) : UserInfo {
    return getUserPassResult(authKey, isSignUp, buildUserPass).userInfo
}

suspend fun UserSessionManager.getUserPassResult(
    authKey: AuthKey,
    isSignUp: Boolean,
    buildUserPass: suspend (RawUserPassInfo) -> UserPass
): SignResult<UserInfo> {
    return signInOrSignUpAndGetUserInfoResult(authKey, isSignUp) { param ->
        buildUserPass(getRawUserPassInfoFromAuthKey(param))
    }
}

suspend fun PanelSessionManager.getPanelUserPass(
    authKey: AuthKey,
    isSignUp: Boolean,
    buildUserPass: suspend (RawUserPassInfo) -> UserPass
) : PanelAccountInfo {
    return signOrSignUpAndGetPanelAccountInfo(authKey, isSignUp) { param ->
        buildUserPass(getRawUserPassInfoFromAuthKey(param))
    }
}
