@file:Suppress("detekt.formatting")

package com.storyteller_f.a.client.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.storyteller_f.shared.model.AlgoType
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

suspend fun UserSessionManager.getUserPass(
    pemPrivateKey: String,
    algo: AlgoType,
    isSignUp: Boolean,
    buildUserPass: suspend (RawUserPassInfo) -> UserPass
) : UserInfo {
    return getUserInfo(pemPrivateKey, algo, isSignUp) { userInfo, derPrivateKey, publicKey, address, algo ->
        val raw = RawUserPassInfo(
            derPrivateKey,
            publicKey,
            address,
            algo,
            userInfo.encryptionPrivateKey,
            userInfo.encryptionPublicKey
        )
        buildUserPass(raw)
    }
}

suspend fun PanelSessionManager.getPanelUserPass(
    pemPrivateKey: String,
    algo: AlgoType,
    isSignUp: Boolean,
    buildUserPass: suspend (RawUserPassInfo) -> UserPass
) : PanelAccountInfo {
    return getPanelAccountInfo(pemPrivateKey, algo, isSignUp) { _, derPrivateKey, publicKey, address, algo ->
        val raw = RawUserPassInfo(
            derPrivateKey,
            publicKey,
            address,
            algo,
            null,
            null
        )
        buildUserPass(raw)
    }
}
