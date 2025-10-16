package com.storyteller_f.a.app.core.utils

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.Settings
import com.russhwolf.settings.serialization.decodeValueOrNull
import com.russhwolf.settings.serialization.encodeValue
import com.russhwolf.settings.serialization.removeValue
import com.storyteller_f.a.client.core.ClientSessionState
import com.storyteller_f.a.client.core.RawUserPass
import com.storyteller_f.a.client.core.RawUserPassInfo
import com.storyteller_f.a.client.core.SessionManager
import com.storyteller_f.a.client.core.UserPass
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

interface LoginHistoryManager {
    fun getSavedSession(): SavedSession

    suspend fun addSession(session: RawUserPassInfo): UserPass

    fun buildSession(alias: String): UserPass?

    fun removeSession(session: String)
}

@Serializable
data class LoginHistory(val last: String? = null, val current: String? = null)

class DefaultLoginHistoryManager(val defaultSettings: Settings) : LoginHistoryManager {
    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override fun getSavedSession(): SavedSession {
        //TODO 返回所有记录
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
        val rawUserPass = RawUserPassInfo(session.pemPrivateKey, session.derPublicKey, session.address)
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

expect fun buildLoginHistoryFactory(settings: Settings): LoginHistoryManager

expect fun createSettings(name: String = "a-default"): Settings

fun<U> SessionManager<U>.restoreFromStorage(settings: Settings) {
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
