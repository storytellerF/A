package com.storyteller_f.a.app.compose_app.utils

import com.russhwolf.settings.Settings
import com.storyteller_f.a.client.core.ClientSessionState
import com.storyteller_f.a.client.core.SimpleUserSessionManager
import kotlinx.serialization.Serializable

expect fun createSettings(name: String = "a-default"): Settings

@Serializable
data class LoginHistory(val last: String? = null, val current: String? = null)

fun SimpleUserSessionManager.restoreFromStorage(settings: Settings) {
    val sessionFactory = buildLoginHistoryFactory(settings)
    val (list, _, current) = sessionFactory.getSavedSession()
    if (current != null && list.contains(current)) {
        val session = sessionFactory.buildSession(current)
        if (session != null) {
            model.updateState(ClientSessionState.Success(session))
        }
    }
}

fun clearStorage(settings: Settings) {
    val sessionFactory = buildLoginHistoryFactory(settings)
    sessionFactory.removeSession("default")
}
