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

interface SessionHistoryManager {
    fun getSavedSession(): SavedSession

    suspend fun addSession(session: RawUserPassInfo): UserPass

    fun buildSession(alias: String): UserPass?

    fun removeSession(session: String)

    fun exitSession(alias: String)

    fun logSession(alias: String)
}

/**
 * 登录之后会修改last 和current，如果没有退出登录，再次打开时会读取current 重新登录
 * 如果退出登录current 会被删除
 */
@Serializable
data class SessionHistory(val last: String? = null, val current: String? = null)

class DefaultSessionHistoryManager(val settings: Settings) : SessionHistoryManager {
    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override fun getSavedSession(): SavedSession {
        val list = settings.keys.map {
            it.split(".")[0]
        }.distinct().filter {
            it.startsWith("session_user")
        }.mapNotNull {
            settings.decodeValueOrNull<RawUserPassInfo>(it)?.address
        }
        val history = settings.decodeValueOrNull<SessionHistory>("session_history")
        return SavedSession(list, history)
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override suspend fun addSession(session: RawUserPassInfo): UserPass {
        val address = session.address
        val rawUserPass =
            RawUserPassInfo(session.pemPrivateKey, session.derPublicKey, address)
        settings.encodeValue("session_user_$address", rawUserPass)
        settings.encodeValue("session_history", SessionHistory(address, address))
        return RawUserPass(session)
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override fun buildSession(alias: String): UserPass? {
        val rawUserPass =
            settings.decodeValueOrNull<RawUserPassInfo>("session_user_$alias") ?: return null
        return RawUserPass(rawUserPass)
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override fun removeSession(session: String) {
        settings.removeValue<RawUserPassInfo>("session_user_$session")
        val sessionHistory = settings.decodeValueOrNull<SessionHistory>("session_history")
        if (sessionHistory != null && sessionHistory.last == session) {
            settings.removeValue<SessionHistory>("session_history")
        }
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override fun exitSession(alias: String) {
        val sessionHistory = settings.decodeValueOrNull<SessionHistory>("session_history")
        if (sessionHistory != null && sessionHistory.current == alias) {
            settings.encodeValue("session_history", sessionHistory.copy(current = null))
        }
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override fun logSession(alias: String) {
        settings.encodeValue("session_history", SessionHistory(alias, alias))
    }
}

data class SavedSession(
    val alias: List<String>,
    val history: SessionHistory?
)

expect fun buildLoginHistoryFactory(settings: Settings): SessionHistoryManager

expect fun createSettings(name: String = "a-default"): Settings

fun <U> SessionManager<U>.restoreFromStorage(settings: Settings) {
    val sessionFactory = buildLoginHistoryFactory(settings)
    val (alias, history) = sessionFactory.getSavedSession()
    val current = history?.current
    if (current != null && alias.contains(current)) {
        val session = sessionFactory.buildSession(current)
        if (session != null) {
            model.updateState(ClientSessionState.Success(session))
        }
    }
}
