@file:Suppress("detekt.formatting")

package com.storyteller_f.a.client_lib

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

context(c: CoroutineScope)
fun UserSessionManager.start(): List<Job> {
    return listOf(c.launch {
        listenerUserInfo()
    }, c.launch {
        listenerState()
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
