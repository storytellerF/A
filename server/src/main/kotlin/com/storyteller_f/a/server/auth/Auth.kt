package com.storyteller_f.a.server.auth

import com.maxmind.geoip2.DatabaseReader
import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.BuildConfig
import com.storyteller_f.a.server.auth.CustomCredential.AidCredential
import com.storyteller_f.a.server.auth.CustomCredential.IdCredential
import com.storyteller_f.a.server.remoteIp
import com.storyteller_f.a.server.route.RouteAccounts
import com.storyteller_f.a.server.route.commonRoute
import com.storyteller_f.shared.*
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKey
import com.storyteller_f.shared.utils.filterNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.tables.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

sealed class CustomCredential(open val sig: String) {
    data class IdCredential(val id: PrimaryKey, override val sig: String) : CustomCredential(sig)
    data class AidCredential(val aid: String, override val sig: String) : CustomCredential(sig)
}

private fun HttpAuthHeader.Parameterized.customCredential(): CustomCredential? {
    val sig = parameters.firstOrNull {
        it.name == "sig"
    }?.value
    return if (sig != null) {
        val id = parameters.firstOrNull {
            it.name == "id"
        }?.value?.toLong()
        if (id != null) {
            IdCredential(id, sig)
        } else {
            val aid = parameters.firstOrNull {
                it.name == "aid"
            }?.value
            if (aid != null) {
                AidCredential(aid, sig)
            } else {
                null
            }
        }
    } else {
        null
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

typealias CustomValidator = suspend (UserSession, ApplicationCall, CustomCredential?) -> Any?
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
        val (session) = call.getSession(config.databaseReader)
        val credential = call.customCredential()
        val principal = config.validateFunction(session, call, credential)
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

suspend fun ApplicationCall.respondUnauthorizedResponse(reader: DatabaseReader) {
    val data = getData(reader)
    respond(UnauthorizedResponse(HttpAuthHeader.Single("Custom", data)))
}

private suspend fun RoutingContext.signIn(backend: Backend, reader: DatabaseReader) {
    val pack = call.receive<SignInPack>()
    val data = call.getData(reader)
    val f = finalData(data)
    DatabaseFactory.getUserByAddress(pack.ad).filterNull {
        BadRequestException("user not found")
    }.onSuccess { (info, icon, publicKey) ->
        if (verify(publicKey, pack.sig, f)) {
            toFinalUserInfo(info to icon, backend = backend).onSuccess { value ->
                val id = value.id
                saveSuccessSessionOnFirst(id, reader)
                call.respond(value)
            }.onFailure {
                call.respond(HttpStatusCode.BadRequest, "media service get failed.")
            }
        } else {
            call.respond(HttpStatusCode.BadRequest, "verify failed.")
        }
    }.onFailure {
        call.respond(HttpStatusCode.BadRequest, it.message.toString())
    }
}

private fun RoutingContext.saveSuccessSessionOnFirst(id: PrimaryKey, reader: DatabaseReader) {
    call.getSession(reader).first.let { session ->
        if (session is UserSession.Pending) {
            call.saveSuccessSession(session, id)
        }
    }
}

private suspend fun RoutingContext.signUp(backend: Backend, reader: DatabaseReader) {
    val pack = call.receive<SignUpPack>()
    val data = call.getData(reader)
    val f = finalData(data)
    if (verify(pack.pk, pack.sig, f)) {
        DatabaseFactory.isUserNotExists(pack.pk).mapResult { bool ->
            if (bool) {
                val ad = calcAddress(pack.pk)
                val newId = SnowflakeFactory.nextId()
                val name = backend.nameService.parse(newId)
                DatabaseFactory.createUser(ad, name, newId, pack.pk).mapResult { value ->
                    saveSuccessSessionOnFirst(newId, reader)
                    toFinalUserInfo(value, backend)
                }
            } else {
                Result.failure(BadRequestException("User exists."))
            }
        }.onSuccess {
            call.respond(it)
        }.onFailure { exception ->
            call.respond(HttpStatusCode.BadRequest, exception.message.toString())
        }
    } else {
        call.respond(HttpStatusCode.BadRequest, "Verify failed.")
    }
}

private suspend fun ApplicationCall.checkApiRequest(
    credential: CustomCredential,
    session: UserSession.Pending
): CustomPrincipal? {
    val sig = credential.sig
    return when {
        !BuildConfig.IS_PROD && credential is IdCredential && sig == credential.id.toString() -> {
            val id = credential.id
            if (DatabaseFactory.getRawUserById(id).getOrNull() != null) {
                saveSuccessSession(session, id)
                CustomPrincipal(id)
            } else {
                null
            }
        }

        sig.isNotBlank() && session.data.isNotBlank() -> {
            getUserAuthData(credential).getOrNull()?.let { (pubKey, id) ->
                if (verify(
                        pubKey,
                        sig,
                        finalData(session.data)
                    )
                ) {
                    saveSuccessSession(session, id)
                    CustomPrincipal(id)
                } else {
                    null
                }
            }
        }

        else -> {
            null
        }
    }
}

private suspend fun getUserAuthData(credential: CustomCredential): Result<Pair<String, Long>?> {
    return when (credential) {
        is AidCredential -> DatabaseFactory.getUserAuthDataByAid {
            Aids.value eq credential.aid
        }
        is IdCredential -> DatabaseFactory.getUserAuthDataBy {
            Users.id eq credential.id
        }
    }
}

private fun ApplicationCall.saveSuccessSession(
    session: UserSession.Pending,
    id: PrimaryKey
) {
    sessions.set<UserSession>(UserSession.Success(session.data, session.remote, id))
}

private suspend fun checkDevWsLink(call: ApplicationCall): CustomPrincipal? {
    val did = call.request.queryParameters["did"]
    return if (did?.all { it.isDigit() } == true) {
        val id = did.toPrimaryKey()
        DatabaseFactory.checkUserExists(id).getOrNull()?.let {
            CustomPrincipal(it)
        }
    } else {
        null
    }
}

private fun ApplicationCall.getData(reader: DatabaseReader): String {
    val (_, data) = getSession(reader)
    return data
}

@OptIn(ExperimentalUuidApi::class)
private fun ApplicationCall.getSession(reader: DatabaseReader): Pair<UserSession, String> {
    val remote = remoteIp(reader).first().first
    return when (val session = sessions.get(UserSession::class)) {
        null -> {
            val data = Uuid.random().toString()
            val newSession = UserSession.Pending(data, remote)
            sessions.set<UserSession>(newSession)
            newSession to data
        }

        is UserSession.Pending -> {
            if (remote == session.remote) {
                session to session.data
            } else {
                val data = Uuid.random().toString()
                val value = UserSession.Pending(data, remote)
                sessions.set<UserSession>(value)
                value to data
            }
        }

        is UserSession.Success -> {
            if (remote == session.remote) {
                session to session.data
            } else {
                val data = Uuid.random().toString()
                val value = UserSession.Pending(data, remote)
                sessions.set<UserSession>(value)
                value to data
            }
        }
    }
}

fun ApplicationCall.getRateLimitKey(databaseReader: DatabaseReader): Comparable<*> {
    return when (val session = getSession(databaseReader).first) {
        is UserSession.Success -> session.id
        is UserSession.Pending -> session.remote
    }
}

fun Application.configureAuth(backend: Backend, reader: DatabaseReader) {
    install(Authentication) {
        custom {
            databaseReader = reader
            validate { session, call, credential ->
                when (session) {
                    is UserSession.Success -> CustomPrincipal(session.id)
                    is UserSession.Pending -> {
                        when {
                            credential != null -> call.checkApiRequest(credential, session)
                            backend.config.isProd -> null
                            else -> checkDevWsLink(call)
                        }
                    }
                }
            }
            challenge { _, call ->
                call.respondUnauthorizedResponse(reader)
            }
        }
    }
    commonRoute(backend, reader)
}

fun Route.bindUnprotectedAccountRoute(backend: Backend, databaseReader: DatabaseReader) {
    get<RouteAccounts.GetData> {
        call.respondText(call.getData(databaseReader))
    }

    post<RouteAccounts.SignUp> {
        signUp(backend, databaseReader)
    }

    post<RouteAccounts.SignIn> {
        signIn(backend, databaseReader)
    }
}

fun Route.bindProtectedAccountRoute(reader: DatabaseReader) {
    post<RouteAccounts.SignOut> {
        usePrincipal(reader) { _ ->
            call.sessions.clear(UserSession::class)
            Result.success(Unit)
        }
    }
}
