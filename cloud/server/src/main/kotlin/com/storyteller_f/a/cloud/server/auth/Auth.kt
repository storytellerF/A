package com.storyteller_f.a.cloud.server.auth

import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.cloud.server.route.checkAdminApiRequest
import com.storyteller_f.a.cloud.server.route.checkApiRequest
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKey
import com.storyteller_f.shared.utils.*
import io.github.aakira.napier.Napier
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

sealed class CustomCredential(open val sig: String) {
    data class IdCredential(val id: PrimaryKey, override val sig: String) : CustomCredential(sig)
    data class AidCredential(val aid: String, override val sig: String) : CustomCredential(sig)
    data class AddressCredential(val ad: String, override val sig: String) : CustomCredential(sig)
}

private fun HttpAuthHeader.Parameterized.customCredential(): CustomCredential? {
    if (parameters.size > 2) return null
    val listMap = parameters.associate {
        it.name to it.value
    }
    val sig = listMap["sig"] ?: return null
    return listMap["id"]?.toLongOrNull()?.let {
        CustomCredential.IdCredential(it, sig)
    } ?: listMap["aid"]?.let {
        CustomCredential.AidCredential(it, sig)
    } ?: listMap["ad"]?.let {
        CustomCredential.AddressCredential(it, sig)
    }
}

data class CustomPrincipal(val uid: PrimaryKey)

inline fun AuthenticationConfig.custom(
    name: String? = null,
    configure: CustomAuthProvider.Config.() -> Unit
) {
    val provider = CustomAuthProvider(CustomAuthProvider.Config(name).apply(configure))
    register(provider)
}

typealias CustomValidator = suspend (UserSession, ApplicationCall, CustomCredential?) -> Result<CustomPrincipal?>
typealias CustomChallenge = suspend (UserSession, ApplicationCall) -> Unit

class CustomAuthProvider(private val config: Config) : AuthenticationProvider(config) {

    class Config(name: String?) : AuthenticationProvider.Config(name) {

        lateinit var validateFunction: CustomValidator
        lateinit var challengeFunction: CustomChallenge

        fun validate(f: CustomValidator) {
            validateFunction = f
        }

        fun challenge(f: CustomChallenge) {
            challengeFunction = f
        }
    }

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val session = call.getSession()
        val credential = call.customCredential()
        val principal = config.validateFunction(session, call, credential).getOrNull()
        if (principal != null) {
            context.principal(name, principal)
        } else {
            val cause =
                if (credential == null) {
                    AuthenticationFailedCause.NoCredentials
                } else {
                    AuthenticationFailedCause.InvalidCredentials
                }

            @Suppress("NAME_SHADOWING")
            context.challenge("CustomChallengeKey", cause) { challenge, call ->
                config.challengeFunction(session, call)
                challenge.complete()
            }
        }
    }

    private fun ApplicationCall.customCredential(): CustomCredential? {
        return when (val authHeader = request.parseAuthorizationHeader()) {
            is HttpAuthHeader.Parameterized -> {
                if (!authHeader.authScheme.equals("Custom", ignoreCase = true)) return null
                authHeader.customCredential()
            }

            else -> null
        }
    }
}

suspend fun ApplicationCall.respondUnauthorizedResponse() {
    val data = getData()
    respond(UnauthorizedResponse(HttpAuthHeader.Single("Custom", data)))
}

fun ApplicationCall.saveSuccessSession(
    session: UserSession.Pending,
    id: PrimaryKey
) {
    sessions.set<UserSession>(UserSession.Success(session.data, session.remote, id, session.label))
}

private suspend fun Backend.checkDevWsLink(call: ApplicationCall): Result<CustomPrincipal?> {
    val did = call.request.queryParameters["did"]
    return if (did?.all { it.isDigit() } == true) {
        val id = did.toPrimaryKey()
        combinedDatabase.userDatabase.getRawUser(ObjectFetch.IdFetch(id)).mapIfNotNull {
            CustomPrincipal(id)
        }
    } else {
        Result.success(null)
    }
}

fun ApplicationCall.getData(): String {
    return getSession().data
}
fun ApplicationCall.getSession(): UserSession {
    val remote = request.origin.remoteAddress
    return when (val session = sessions.get(UserSession::class)) {
        null -> {
            val newSession = createPendingSession(remote)
            sessions.set<UserSession>(newSession)
            newSession
        }

        is UserSession.Pending -> {
            if (remote == session.remote) {
                session
            } else {
                val value = createPendingSession(remote)
                sessions.set<UserSession>(value)
                value
            }
        }

        is UserSession.Success -> {
            if (remote == session.remote) {
                session
            } else {
                val value = createPendingSession(remote)
                sessions.set<UserSession>(value)
                value
            }
        }
    }
}

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
private fun ApplicationCall.createPendingSession(remote: String): UserSession.Pending {
    val aTs = request.header("a-ts")?.toLongOrNull()
    val data = when {
        aTs != null && checkTsIsValid(aTs, 60 * 5).second -> aTs.toString()
        else -> Uuid.random().toString()
    }
    val type = if (request.path().startsWith("/admin")) "panel" else "user"
    val value = UserSession.Pending(data, remote, type)
    return value
}

fun ApplicationCall.getRateLimitKey(): Comparable<*> {
    if (request.uri == "/metrics") return "metrics"
    return when (val session = getSession()) {
        is UserSession.Success -> session.id
        is UserSession.Pending -> session.remote
    }
}

fun Application.configureAuth(backend: Backend) {
    install(Authentication) {
        custom("user") {
            validate { session, call, credential ->
                when (session) {
                    is UserSession.Success -> Result.success(CustomPrincipal(session.id))
                    is UserSession.Pending -> {
                        when {
                            credential != null -> call.checkApiRequest(backend, credential, session)
                            backend.customConfig.buildType == "prod" -> Result.success(null)
                            else -> backend.checkDevWsLink(call)
                        }
                    }
                }
            }
            challenge { _, call ->
                call.respondUnauthorizedResponse()
            }
        }
        custom("admin") {
            validate { session, call, credential ->
                when (session) {
                    is UserSession.Success -> Result.success(CustomPrincipal(session.id))
                    is UserSession.Pending -> {
                        if (credential != null) {
                            call.checkAdminApiRequest(backend, credential, session)
                        } else {
                            Result.success(null)
                        }
                    }
                }
            }
            challenge { _, call ->
                call.respondUnauthorizedResponse()
            }
        }
    }
}
