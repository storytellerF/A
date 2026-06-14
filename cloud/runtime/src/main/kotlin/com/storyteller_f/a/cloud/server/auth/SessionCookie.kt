package com.storyteller_f.a.cloud.server.auth

import com.storyteller_f.a.backend.core.MergedEnv
import io.github.aakira.napier.Napier
import io.ktor.server.sessions.SessionTransportTransformerEncrypt
import io.ktor.server.sessions.SessionsConfig
import io.ktor.server.sessions.cookie
import java.security.MessageDigest
import java.security.SecureRandom

private data class SessionKeys(
    val encryptKey: ByteArray,
    val signKey: ByteArray,
)

fun SessionsConfig.setupUserSessions(env: MergedEnv) {
    val keys = sessionKeys(env)
    cookie<UserSession>("user_session") {
        cookie.path = "/"
        cookie.maxAgeInSeconds = 3600
        transform(SessionTransportTransformerEncrypt(keys.encryptKey, keys.signKey))
    }
}

private fun sessionKeys(env: MergedEnv): SessionKeys {
    val secret = env["SESSION_SECRET"]
    if (!secret.isNullOrBlank()) {
        return deriveSessionKeys(secret)
    }
    Napier.w(tag = "session") {
        "SESSION_SECRET is empty, generating process-local session keys"
    }
    return randomSessionKeys()
}

private fun deriveSessionKeys(secret: String): SessionKeys {
    return SessionKeys(
        encryptKey = sha256("encrypt:$secret").copyOf(16),
        signKey = sha256("sign:$secret"),
    )
}

private fun randomSessionKeys(): SessionKeys {
    val secureRandom = SecureRandom()
    return SessionKeys(
        encryptKey = ByteArray(16).apply { secureRandom.nextBytes(this) },
        signKey = ByteArray(32).apply { secureRandom.nextBytes(this) },
    )
}

private fun sha256(value: String): ByteArray {
    return MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
}
