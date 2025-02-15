package com.storyteller_f.a.app.utils

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.serialization.decodeValueOrNull
import com.russhwolf.settings.serialization.encodeValue
import com.russhwolf.settings.serialization.removeValue
import com.storyteller_f.a.client_lib.DefaultLoginUserSession
import com.storyteller_f.a.client_lib.LoginUser
import com.storyteller_f.a.client_lib.LoginUserSession
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.serializer

interface LoginUserSessionManager {
    fun savedSession(): SavedSession

    fun addSession(session: LoginUser): LoginUserSession

    fun buildSession(alias: String): LoginUserSession?

    fun removeSession(session: String)
}

class DefaultLoginUserSessionManager : LoginUserSessionManager {
    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override fun savedSession(): SavedSession {
        val loginUser = defaultSettings.decodeValueOrNull<LoginUser>("login_user")
        return if (loginUser != null) {
            val loginHistory = defaultSettings.decodeValueOrNull<LoginHistory>("login_history")
            SavedSession(listOf("default"), loginHistory?.last, loginHistory?.current)
        } else {
            SavedSession(emptyList())
        }
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override fun addSession(session: LoginUser): LoginUserSession {
        val loginUser = LoginUser(session.privateKey, session.publicKey, session.address)
        defaultSettings.encodeValue("login_user", loginUser)
        defaultSettings.encodeValue("login_history", LoginHistory("default", "default"))
        return DefaultLoginUserSession(session)
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override fun buildSession(alias: String): LoginUserSession? {
        val loginUser = defaultSettings.decodeValueOrNull<LoginUser>("login_user") ?: return null
        return DefaultLoginUserSession(loginUser)
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override fun removeSession(session: String) {
        defaultSettings.removeValue(LoginUser.serializer(), "login_user")
    }
}

data class SavedSession(val list: List<String>, val last: String? = null, val current: String? = null)

expect fun buildLoginUserSessionFactory(): LoginUserSessionManager
