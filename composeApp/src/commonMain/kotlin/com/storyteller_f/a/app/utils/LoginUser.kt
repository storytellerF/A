package com.storyteller_f.a.app.utils

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.Settings
import com.russhwolf.settings.serialization.decodeValueOrNull
import com.russhwolf.settings.serialization.encodeValue
import com.russhwolf.settings.serialization.removeValue
import com.storyteller_f.a.client_lib.RawUserPass
import com.storyteller_f.a.client_lib.RawUserPassInfo
import com.storyteller_f.a.client_lib.UserPass
import kotlinx.serialization.ExperimentalSerializationApi

interface LoginUserSessionManager {
    fun savedSession(): SavedSession

    suspend fun addSession(session: RawUserPassInfo): UserPass

    fun buildSession(alias: String): UserPass?

    fun removeSession(session: String)
}

class DefaultLoginUserSessionManager(val defaultSettings: Settings) : LoginUserSessionManager {
    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override fun savedSession(): SavedSession {
        val rawUserPass = defaultSettings.decodeValueOrNull<RawUserPassInfo>("login_user")
        return if (rawUserPass != null) {
            val loginHistory = defaultSettings.decodeValueOrNull<LoginHistory>("login_history")
            SavedSession(listOf("default"), loginHistory?.last, loginHistory?.current)
        } else {
            SavedSession(emptyList())
        }
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override suspend fun addSession(session: RawUserPassInfo): UserPass {
        val rawUserPass = RawUserPassInfo(session.privateKey, session.publicKey, session.address)
        defaultSettings.encodeValue("login_user", rawUserPass)
        defaultSettings.encodeValue("login_history", LoginHistory("default", "default"))
        return RawUserPass(session)
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override fun buildSession(alias: String): UserPass? {
        val rawUserPass = defaultSettings.decodeValueOrNull<RawUserPassInfo>("login_user") ?: return null
        return RawUserPass(rawUserPass)
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override fun removeSession(session: String) {
        defaultSettings.removeValue(RawUserPassInfo.serializer(), "login_user")
    }
}

data class SavedSession(val list: List<String>, val last: String? = null, val current: String? = null)

expect fun buildLoginUserSessionFactory(settings: Settings): LoginUserSessionManager

expect fun unregisterPushService()
