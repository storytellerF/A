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

    fun exitSession(alias: String)

    fun logSession(alias: String)
}

/**
 * 登录之后会修改last 和current，如果没有退出登录，再次打开时会读取current 重新登录
 * 如果退出登录current 会被删除
 */
@Serializable
data class LoginHistory(val last: String? = null, val current: String? = null)

class DefaultLoginHistoryManager(val settings: Settings) : LoginHistoryManager {
    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override fun getSavedSession(): SavedSession {
        val list = settings.keys.map {
            it.split(".")[0]
        }.distinct().filter {
            it.startsWith("login_user")
        }.mapNotNull {
            settings.decodeValueOrNull<RawUserPassInfo>(it)?.address
        }
        val loginHistory = settings.decodeValueOrNull<LoginHistory>("login_history")
        return SavedSession(list, loginHistory)
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override suspend fun addSession(session: RawUserPassInfo): UserPass {
        val address = session.address
        val rawUserPass =
            RawUserPassInfo(session.pemPrivateKey, session.derPublicKey, address)
        settings.encodeValue("login_user_$address", rawUserPass)
        settings.encodeValue("login_history", LoginHistory(address, address))
        return RawUserPass(session)
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override fun buildSession(alias: String): UserPass? {
        val rawUserPass =
            settings.decodeValueOrNull<RawUserPassInfo>("login_user_$alias") ?: return null
        return RawUserPass(rawUserPass)
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override fun removeSession(session: String) {
        settings.removeValue<RawUserPassInfo>("login_user_$session")
        val loginHistory = settings.decodeValueOrNull<LoginHistory>("login_history")
        if (loginHistory != null && loginHistory.last == session) {
            settings.removeValue<LoginHistory>("login_history")
        }
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override fun exitSession(alias: String) {
        val loginHistory = settings.decodeValueOrNull<LoginHistory>("login_history")
        if (loginHistory != null && loginHistory.current == alias) {
            settings.encodeValue("login_history", loginHistory.copy(current = null))
        }
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override fun logSession(alias: String) {
        settings.encodeValue("login_history", LoginHistory(alias, alias))
    }
}

data class SavedSession(
    val list: List<String>,
    val history: LoginHistory?
)

expect fun buildLoginHistoryFactory(settings: Settings): LoginHistoryManager

expect fun createSettings(name: String = "a-default"): Settings

fun <U> SessionManager<U>.restoreFromStorage(settings: Settings) {
    val sessionFactory = buildLoginHistoryFactory(settings)
    val (list, history) = sessionFactory.getSavedSession()
    val alias = history?.current
    if (alias != null && list.contains(alias)) {
        val session = sessionFactory.buildSession(alias)
        if (session != null) {
            model.updateState(ClientSessionState.Success(session))
        }
    }
}

suspend fun <U> SessionManager<U>.clearStorage(settings: Settings) {
    val sessionFactory = buildLoginHistoryFactory(settings)
    val alias = model.currentUserPass?.address()?.getOrThrow() ?: return
    sessionFactory.exitSession(alias)
}
