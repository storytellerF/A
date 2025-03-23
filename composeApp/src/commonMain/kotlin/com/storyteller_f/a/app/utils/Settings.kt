package com.storyteller_f.a.app.utils

import com.russhwolf.settings.Settings
import com.storyteller_f.a.client_lib.ClientSession
import com.storyteller_f.a.client_lib.LoginViewModel
import kotlinx.serialization.Serializable

expect val defaultSettings: Settings

@Serializable
data class LoginHistory(val last: String? = null, val current: String? = null)

fun restoreFromStorage() {
    val sessionFactory = buildLoginUserSessionFactory()
    val (list, _, current) = sessionFactory.savedSession()
    if (current != null && list.contains(current)) {
        val session = sessionFactory.buildSession(current)
        if (session != null) {
            LoginViewModel.updateState(ClientSession.SignUpSuccess(session))
        }
    }
}

fun clearStorage() {
    val sessionFactory = buildLoginUserSessionFactory()
    sessionFactory.removeSession("default")
}
