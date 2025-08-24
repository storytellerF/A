package com.storyteller_f.a.cloud.server.auth

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.a.api.core.CustomApi
import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.a.cloud.core.service.addAlternativeAccount
import com.storyteller_f.a.cloud.core.service.getUserAlternateUserInfoList
import com.storyteller_f.a.cloud.server.ServerConfig
import com.storyteller_f.a.cloud.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.cloud.server.common.pagination
import com.storyteller_f.a.cloud.server.route.checkApiRequest
import com.storyteller_f.a.cloud.server.route.signIn
import com.storyteller_f.a.cloud.server.route.signUp
import com.storyteller_f.route4k.ktor.server.invoke
import com.storyteller_f.route4k.ktor.server.receiveBody
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
import io.ktor.server.routing.*
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

        lateinit var databaseReader: DatabaseReader
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
        val (session) = call.getSession()
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
    sessions.set<UserSession>(UserSession.Success(session.data, session.remote, id))
}

private suspend fun Backend.checkDevWsLink(call: ApplicationCall): Result<CustomPrincipal?> {
    val did = call.request.queryParameters["did"]
    return if (did?.all { it.isDigit() } == true) {
        val id = did.toPrimaryKey()
        combinedDatabase.userDatabase.isUserExistsByUid(id).map {
            CustomPrincipal(id)
        }
    } else {
        Result.success(null)
    }
}

fun ApplicationCall.getData(): String {
    val (_, data) = getSession()
    return data
}

fun ApplicationCall.getSession(): Pair<UserSession, String> {
    val remote = request.origin.remoteAddress
    return when (val session = sessions.get(UserSession::class)) {
        null -> {
            val (data, newSession) = createPendingSession(remote)
            sessions.set<UserSession>(newSession)
            newSession to data
        }

        is UserSession.Pending -> {
            if (remote == session.remote) {
                session to session.data
            } else {
                val (data, value) = createPendingSession(remote)
                sessions.set<UserSession>(value)
                value to data
            }
        }

        is UserSession.Success -> {
            if (remote == session.remote) {
                session to session.data
            } else {
                val (data, value) = createPendingSession(remote)
                sessions.set<UserSession>(value)
                value to data
            }
        }
    }
}

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
private fun ApplicationCall.createPendingSession(remote: String): Pair<String, UserSession.Pending> {
    Napier.i {
        "pending session $remote"
    }
    val aTs = request.header("a-ts")?.toLongOrNull()
    val data = when {
        aTs != null && checkTsIsValid(aTs, 60 * 5).second -> aTs.toString()
        else -> Uuid.random().toString()
    }
    val value = UserSession.Pending(data, remote)
    return Pair(data, value)
}

fun ApplicationCall.getRateLimitKey(): Comparable<*> {
    return when (val session = getSession().first) {
        is UserSession.Success -> session.id
        is UserSession.Pending -> session.remote
    }
}

fun Application.configureAuth(reader: DatabaseReader, backend: Backend) {
    install(Authentication) {
        custom {
            databaseReader = reader
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
    }
}

fun Route.bindUnprotectedAccountRoute(
    backend: Backend
) {
    CustomApi.Accounts.getData.invoke(RoutingContext::handleResult) {
        Result.success(call.getData())
    }
    CustomApi.Accounts.signUp.invoke(RoutingContext::handleResult) {
        @Suppress("KotlinConstantConditions")
        if (ServerConfig.IS_PROD) {
            Result.failure(Exception("not support"))
        } else {
            signUp(backend, it.receiveBody())
        }
    }

    CustomApi.Accounts.signIn.invoke(RoutingContext::handleResult) {
        signIn(backend, it.receiveBody(), call.getData())
    }
}

fun Route.bindAccountRoute() {
    CustomApi.Accounts.signOut.invoke(RoutingContext::handleResult) {
        usePrincipalOrNull { uid ->
            call.sessions.clear(UserSession::class)
            Result.success(Unit)
        }
    }
}

fun Route.bindProtectedAccountRoute(backend: Backend) {
    CustomApi.Accounts.ChildAccounts.add.invoke(RoutingContext::handleResult) {
        usePrincipal { uid ->
            backend.addAlternativeAccount(uid)
        }
    }
    CustomApi.Accounts.ChildAccounts.get.invoke(RoutingContext::handleResult) {
        usePrincipal { uid ->
            pagination(IdentifiablePagingGenerator) {
                backend.getUserAlternateUserInfoList(uid, it)
            }
        }
    }
}
