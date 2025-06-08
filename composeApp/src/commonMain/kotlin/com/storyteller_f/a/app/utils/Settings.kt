package com.storyteller_f.a.app.utils

import androidx.compose.runtime.Composable
import com.russhwolf.settings.Settings
import com.storyteller_f.a.client_lib.ClientSessionState
import com.storyteller_f.a.client_lib.UserSessionManager
import com.strabled.composepreferences.utilis.DataStoreManager
import kotlinx.serialization.Serializable

expect fun createSettings(name: String = "a-default"): Settings

@Composable
expect fun createCustomDataStoreManager(): DataStoreManager

@Serializable
data class LoginHistory(val last: String? = null, val current: String? = null)

fun UserSessionManager.restoreFromStorage(settings: Settings) {
    val sessionFactory = buildLoginUserSessionFactory(settings)
    val (list, _, current) = sessionFactory.savedSession()
    if (current != null && list.contains(current)) {
        val session = sessionFactory.buildSession(current)
        if (session != null) {
            sessionModel.updateState(ClientSessionState.Success(session))
        }
    }
}

fun clearStorage(settings: Settings) {
    val sessionFactory = buildLoginUserSessionFactory(settings)
    sessionFactory.removeSession("default")
}
