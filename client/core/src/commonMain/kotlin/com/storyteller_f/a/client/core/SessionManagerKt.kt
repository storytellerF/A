@file:Suppress("detekt.formatting")

package com.storyteller_f.a.client.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

context(c: CoroutineScope)
fun UserSessionManager.start(): List<Job> {
    return listOf(c.launch {
        val model = sessionModel
        combine(model.state, model.userHandler.data) { t1, t2 ->
            t1 to t2
        }.distinctUntilChanged().collect { (state, userInfo) ->
            if (state is ClientSessionState.Success && userInfo == null) {
                login()
            }
        }
    }, c.launch {
        sessionModel.state.collect {
            address.value = (it as? ClientSessionState.Success)?.session?.address()?.getOrNull()
            isAlreadySignUp.value = it is ClientSessionState.Success
        }
    }, c.launch {
        webSocketClient.start()
    })
}

context(c: CoroutineScope)
inline fun <R> UserSessionManager.start(block: UserSessionManager.() -> R): R {
    val jobs = start()
    val result = block()
    jobs.forEach(Job::cancel)
    return result
}
