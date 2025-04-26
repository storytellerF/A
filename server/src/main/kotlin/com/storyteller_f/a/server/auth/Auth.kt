package com.storyteller_f.a.server.auth

import com.maxmind.geoip2.DatabaseReader
import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.Backend
import com.storyteller_f.CustomBadRequestException
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.ServerConfig
import com.storyteller_f.a.server.auth.CustomCredential.*
import com.storyteller_f.a.server.remoteIp
import com.storyteller_f.a.server.route.RouteAccounts
import com.storyteller_f.a.server.route.commonRoute
import com.storyteller_f.shared.*
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKey
import com.storyteller_f.shared.utils.checkTsIsValid
import com.storyteller_f.shared.utils.filterNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.tables.*
import io.github.aakira.napier.Napier
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
    data class AddressCredential(val ad: String, override val sig: String) : CustomCredential(sig)
}

private fun HttpAuthHeader.Parameterized.customCredential(): CustomCredential? {
    if (parameters.size > 2) return null
    val listMap = parameters.associate {
        it.name to it.value
    }
    val sig = listMap["sig"] ?: return null
    return listMap["id"]?.toLongOrNull()?.let {
        IdCredential(it, sig)
    } ?: listMap["aid"]?.let {
        AidCredential(it, sig)
    } ?: listMap["ad"]?.let {
        AddressCredential(it, sig)
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

private suspend fun RoutingContext.signIn(
    backend: Backend,
    reader: DatabaseReader,
    pack: SignInPack
): Result<UserInfo> {
    val data = call.getData(reader)
    val f = finalData(data)
    return DatabaseFactory.getUserByAddress(backend, pack.ad).filterNull {
        BadRequestException("user not found")
    }.mapResult { (info, icon, publicKey) ->
        if (verify(publicKey, pack.sig, f)) {
            processUserList(backend, listOf(info to icon)).map {
                it.first()
            }.map { value ->
                val id = value.id
                saveSuccessSessionOnFirst(id, reader)
                value
            }
        } else {
            Result.failure(BadRequestException("Verify failed"))
        }
    }
}

private fun RoutingContext.saveSuccessSessionOnFirst(id: PrimaryKey, reader: DatabaseReader) {
    call.getSession(reader).first.let { session ->
        if (session is UserSession.Pending) {
            call.saveSuccessSession(session, id)
        }
    }
}

private suspend fun RoutingContext.signUp(
    backend: Backend,
    reader: DatabaseReader,
    pack: SignUpPack
): Result<UserInfo> {
    val data = call.getData(reader)
    val f = finalData(data)
    return if (verify(pack.pk, pack.sig, f)) {
        DatabaseFactory.isUserNotExists(backend, pack.pk).mapResult { bool ->
            if (bool) {
                val ad = calcAddress(pack.pk)
                val newId = SnowflakeFactory.nextId()
                val name = backend.nameService.parse(newId)
                DatabaseFactory.createUser(backend, ad, name, newId, pack.pk).mapResult { value ->
                    saveSuccessSessionOnFirst(newId, reader)
                    processUserList(backend, listOf<Pair<UserInfo, String?>>(value)).map {
                        it.first()
                    }
                }
            } else {
                Result.failure(BadRequestException("User exists"))
            }
        }
    } else {
        Result.failure(CustomBadRequestException("Verify failed"))
    }
}

private suspend fun ApplicationCall.checkApiRequest(
    backend: Backend,
    credential: CustomCredential,
    session: UserSession.Pending
): CustomPrincipal? {
    val sig = credential.sig
    return when {
        !ServerConfig.IS_PROD && credential is IdCredential && sig == credential.id.toString() -> {
            val id = credential.id
            if (DatabaseFactory.getRawUserById(backend, id).getOrNull() != null) {
                saveSuccessSession(session, id)
                CustomPrincipal(id)
            } else {
                null
            }
        }

        sig.isNotBlank() && session.data.isNotBlank() -> {
            getUserAuthData(backend, credential).getOrNull()?.let { (pubKey, id) ->
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

private suspend fun getUserAuthData(
    backend: Backend,
    credential: CustomCredential
): Result<Pair<String, Long>?> {
    return when (credential) {
        is AidCredential -> DatabaseFactory.getUserAuthDataByAid(backend) {
            Aids.value eq credential.aid
        }

        is IdCredential -> DatabaseFactory.getUserAuthDataBy(backend) {
            Users.id eq credential.id
        }

        is AddressCredential -> DatabaseFactory.getUserAuthDataBy(backend) {
            Users.address eq credential.ad
        }
    }
}

private fun ApplicationCall.saveSuccessSession(
    session: UserSession.Pending,
    id: PrimaryKey
) {
    sessions.set<UserSession>(UserSession.Success(session.data, session.remote, id))
}

private suspend fun checkDevWsLink(backend: Backend, call: ApplicationCall): CustomPrincipal? {
    val did = call.request.queryParameters["did"]
    return if (did?.all { it.isDigit() } == true) {
        val id = did.toPrimaryKey()
        DatabaseFactory.checkUserExists(backend, id).getOrNull()?.let {
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

private fun ApplicationCall.getSession(reader: DatabaseReader): Pair<UserSession, String> {
    val remote = remoteIp(reader).first().first
    return when (val session = sessions.get(UserSession::class)) {
        null -> {
            val (data, newSession) = this.createPendingSession(remote)
            sessions.set<UserSession>(newSession)
            newSession to data
        }

        is UserSession.Pending -> {
            if (remote == session.remote) {
                session to session.data
            } else {
                val (data, value) = this.createPendingSession(remote)
                sessions.set<UserSession>(value)
                value to data
            }
        }

        is UserSession.Success -> {
            if (remote == session.remote) {
                session to session.data
            } else {
                val (data, value) = this.createPendingSession(remote)
                sessions.set<UserSession>(value)
                value to data
            }
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
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

fun ApplicationCall.getRateLimitKey(databaseReader: DatabaseReader): Comparable<*> {
    return when (val session = getSession(databaseReader).first) {
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
                    is UserSession.Success -> CustomPrincipal(session.id)
                    is UserSession.Pending -> {
                        when {
                            credential != null -> call.checkApiRequest(backend, credential, session)
                            backend.config.isProd -> null
                            else -> checkDevWsLink(backend, call)
                        }
                    }
                }
            }
            challenge { _, call ->
                call.respondUnauthorizedResponse(reader)
            }
        }
    }
    commonRoute(reader, backend)
}

fun Route.bindUnprotectedAccountRoute(
    databaseReader: DatabaseReader,
    backend: Backend
) {
    get<RouteAccounts.GetData> {
        omitPrincipal(databaseReader) {
            Result.success(call.getData(databaseReader))
        }
    }

    post<RouteAccounts.SignUp> {
        omitPrincipal(databaseReader) {
            signUp(backend, databaseReader, call.receive<SignUpPack>())
        }
    }

    post<RouteAccounts.SignIn> {
        omitPrincipal(databaseReader) {
            signIn(backend, databaseReader, call.receive<SignInPack>())
        }
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
