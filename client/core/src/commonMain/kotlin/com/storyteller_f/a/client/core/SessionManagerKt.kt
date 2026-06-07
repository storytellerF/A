@file:Suppress("detekt.formatting")

package com.storyteller_f.a.client.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.storyteller_f.shared.model.PanelAccountInfo
import com.storyteller_f.shared.model.UserInfo
import io.github.aakira.napier.Napier

context(c: CoroutineScope)
fun UserSessionManager.startBackgroundTask(): List<Job> {
    val model = model
    val loginRequests = Channel<Unit>(Channel.CONFLATED)
    val loginWorker = c.launch {
        for (ignored in loginRequests) {
            if (model.state.value is ClientSessionState.Success && model.userHandler.data.value == null) {
                login()
            }
        }
    }
    val loginObserver = c.launch {
        combine(model.state, model.userHandler.data) { t1, t2 ->
            t1 to t2
        }.distinctUntilChanged().collect { (state, userInfo) ->
            Napier.i(tag = "UserSessionManager") {
                "background task state=${state::class.simpleName} hasUser=${userInfo != null}"
            }
            if (state is ClientSessionState.Success && userInfo == null) {
                loginRequests.trySend(Unit)
            }
        }
    }
    val addressUpdater = c.launch {
        model.state.collect {
            updateAddress(it)
        }
    }
    val webSocketConnector = c.launch {
        webSocketClient.connectWebSocket()
    }
    return listOf(loginObserver, loginWorker, addressUpdater, webSocketConnector)
}

context(c: CoroutineScope)
suspend inline fun <R> UserSessionManager.onBackgroundTask(block: UserSessionManager.() -> R): R {
    val jobs = startBackgroundTask()
    val result = block()
    jobs.forEach {
        it.cancelAndJoin()
    }
    return result
}

context(c: CoroutineScope)
fun PanelSessionManager.startBackgroundTask(): List<Job> {
    val model = model
    val loginRequests = Channel<Unit>(Channel.CONFLATED)
    val loginWorker = c.launch {
        for (ignored in loginRequests) {
            if (model.state.value is ClientSessionState.Success && model.userHandler.data.value == null) {
                login()
            }
        }
    }
    val loginObserver = c.launch {
        combine(model.state, model.userHandler.data) { t1, t2 ->
            t1 to t2
        }.distinctUntilChanged().collect { (state, userInfo) ->
            if (state is ClientSessionState.Success && userInfo == null) {
                loginRequests.trySend(Unit)
            }
        }
    }
    val addressUpdater = c.launch {
        model.state.collect {
            updateAddress(it)
        }
    }
    return listOf(loginObserver, loginWorker, addressUpdater)
}

context(c: CoroutineScope)
suspend inline fun <R> PanelSessionManager.onBackgroundTask(block: PanelSessionManager.() -> R): R {
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
