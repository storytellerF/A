package com.storyteller_f.a.app.core.utils

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.Settings
import com.russhwolf.settings.serialization.decodeValueOrNull
import com.russhwolf.settings.serialization.encodeValue
import com.russhwolf.settings.serialization.removeValue
import com.storyteller_f.a.client.core.AuthKey
import com.storyteller_f.a.client.core.ClientSessionState
import com.storyteller_f.a.client.core.RawUserPass
import com.storyteller_f.a.client.core.RawUserPassInfo
import com.storyteller_f.a.client.core.SessionManager
import com.storyteller_f.a.client.core.UserPass
import com.storyteller_f.shared.model.AlgoType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

interface SessionHistoryManager {
    fun getSavedSession(): SavedSession

    suspend fun addSession(userPassInfo: RawUserPassInfo): UserPass

    fun buildSession(alias: String): UserPass?

    fun removeSession(alias: String)

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
            it.startsWith(SESSION_USER_PREFIX)
        }.mapNotNull {
            settings.decodeValueOrNull<ConvertedRawUserPassInfo>(it)?.address
        }
        val history = settings.decodeValueOrNull<SessionHistory>(SESSION_HISTORY)
        return SavedSession(list, history)
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override suspend fun addSession(userPassInfo: RawUserPassInfo): UserPass {
        val address = userPassInfo.address
        settings.encodeValue(getUserKey(address), userPassInfo.toConverted())
        settings.encodeValue(SESSION_HISTORY, SessionHistory(address, address))
        return RawUserPass(userPassInfo)
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override fun buildSession(alias: String): UserPass? {
        val rawUserPass = settings.decodeValueOrNull<ConvertedRawUserPassInfo>(getUserKey(alias)) ?: return null
        return RawUserPass(rawUserPass.toRawUserPassInfo())
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override fun removeSession(alias: String) {
        settings.removeValue<ConvertedRawUserPassInfo>(getUserKey(alias))
        val sessionHistory = settings.decodeValueOrNull<SessionHistory>(SESSION_HISTORY)
        if (sessionHistory != null && sessionHistory.last == alias) {
            settings.removeValue<SessionHistory>(SESSION_HISTORY)
        }
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override fun exitSession(alias: String) {
        val sessionHistory = settings.decodeValueOrNull<SessionHistory>(SESSION_HISTORY)
        if (sessionHistory != null && sessionHistory.current == alias) {
            settings.encodeValue(SESSION_HISTORY, sessionHistory.copy(current = null))
        }
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override fun logSession(alias: String) {
        settings.encodeValue(SESSION_HISTORY, SessionHistory(alias, alias))
    }

    companion object {
        const val SESSION_HISTORY = "session_history"
        const val SESSION_USER_PREFIX = "S_U"
        fun getUserKey(address: String): String {
            return "${SESSION_USER_PREFIX}_$address"
        }
    }
}

data class SavedSession(
    val alias: List<String>,
    val history: SessionHistory?
)

expect fun buildSessionHistoryFactory(settings: Settings): SessionHistoryManager

expect fun createSettings(name: String = "a-default"): Settings

fun <U> SessionManager<U>.restoreFromStorage(settings: Settings) {
    val sessionFactory = buildSessionHistoryFactory(settings)
    val (alias, history) = sessionFactory.getSavedSession()
    val current = history?.current
    if (current != null && alias.contains(current)) {
        val session = sessionFactory.buildSession(current)
        if (session != null) {
            model.updateState(ClientSessionState.Success(session))
        }
    }
}

@Serializable
data class ConvertedRawUserPassInfo(
    val algo: AlgoType,
    val address: String,
    val pemPrivateKey: String,
    val derPrivateKey: String,
    val derPublicKey: String,
    val pemEncryptionPrivateKey: String? = null,
    val derEncryptionPrivateKey: String? = null,
    val derEncryptionPublicKey: String? = null,
) {
    fun toRawUserPassInfo(): RawUserPassInfo {
        val key = when (algo) {
            AlgoType.P256 -> AuthKey.P256(
                pemPrivateKey = pemPrivateKey,
                derPrivateKey = derPrivateKey,
                derPublicKey = derPublicKey,
            )

            AlgoType.DILITHIUM -> AuthKey.Dilithium(
                pemPrivateKey = pemPrivateKey,
                derPrivateKey = derPrivateKey,
                derPublicKey = derPublicKey,
                pemEncryptionPrivateKey = pemEncryptionPrivateKey!!,
                derEncryptionPrivateKey = derEncryptionPrivateKey!!,
                derEncryptionPublicKey = derEncryptionPublicKey!!,
            )
        }
        return RawUserPassInfo(address, key)
    }
}

fun RawUserPassInfo.toConverted(): ConvertedRawUserPassInfo {
    return when (val key = authKey) {
        is AuthKey.Dilithium -> ConvertedRawUserPassInfo(
            algo = key.algo,
            address = address,
            pemPrivateKey = key.pemPrivateKey,
            derPrivateKey = key.derPrivateKey,
            derPublicKey = key.derPublicKey,
            pemEncryptionPrivateKey = key.pemEncryptionPrivateKey,
            derEncryptionPrivateKey = key.derEncryptionPrivateKey,
            derEncryptionPublicKey = key.derEncryptionPublicKey,
        )

        is AuthKey.P256 -> ConvertedRawUserPassInfo(
            algo = key.algo,
            address = address,
            pemPrivateKey = key.pemPrivateKey,
            derPrivateKey = key.derPrivateKey,
            derPublicKey = key.derPublicKey,
        )
    }
}
