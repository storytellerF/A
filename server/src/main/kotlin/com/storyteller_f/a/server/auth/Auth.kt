package com.storyteller_f.a.server.auth

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.BuildConfig
import com.storyteller_f.a.server.protectedContent
import com.storyteller_f.a.server.service.toFinalUserInfo
import com.storyteller_f.a.server.service.toUserInfo
import com.storyteller_f.a.server.unProtectedContent
import com.storyteller_f.shared.*
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKey
import com.storyteller_f.shared.utils.filterNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.User
import com.storyteller_f.tables.Users
import com.storyteller_f.tables.createUser
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
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
            CustomCredential.IdCredential(id, sig)
        } else {
            val aid = parameters.firstOrNull {
                it.name == "aid"
            }?.value
            if (aid != null) {
                CustomCredential.AidCredential(aid, sig)
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

fun Application.configureAuth(backend: Backend) {
    install(Authentication) {
        custom {
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
                call.respondUnauthorizedResponse()
            }
        }
    }
    routing {
        authenticate {
            protectedContent(backend)
        }
        authenticate(optional = true) {
            unProtectedContent(backend)
        }

        get("/get_data") {
            call.respondText(call.getData())
        }

        post("/sign_up") {
            signUp(backend)
        }

        post("/sign_in") {
            signIn(backend)
        }

        get("/ping") {
            call.respondText("pong")
        }

        get {
            call.respondText("${backend.config.flavor} ${backend.config.isProd}")
        }
    }
}

suspend fun ApplicationCall.respondUnauthorizedResponse() {
    val data = getData()
    respond(UnauthorizedResponse(HttpAuthHeader.Single("Custom", data)))
}

private suspend fun RoutingContext.signIn(backend: Backend) {
    val pack = call.receive<SignInPack>()
    val data = call.getData()
    val f = finalData(data)
    val userTriple = DatabaseFactory.first({
        Triple(toUserInfo(), icon, publicKey)
    }, User::wrapRow) {
        User.find {
            Users.address eq pack.ad
        }
    }
    userTriple.filterNull {
        BadRequestException("user not found")
    }.onSuccess { (info, icon, publicKey) ->
        if (verify(publicKey, pack.sig, f)) {
            toFinalUserInfo(info to icon, backend = backend).onSuccess { value ->
                val id = value.id
                saveSuccessSessionOnFirst(id)
                call.respond(value)
            }.onFailure { exception ->
                call.respond(HttpStatusCode.BadRequest, "media service get failed.")
            }
        } else {
            call.respond(HttpStatusCode.BadRequest, "verify failed.")
        }
    }.onFailure {
        call.respond(HttpStatusCode.BadRequest, it.message.toString())
    }
}

private fun RoutingContext.saveSuccessSessionOnFirst(id: PrimaryKey) {
    call.getSession().first.let { session ->
        if (session is UserSession.Pending) {
            call.saveSuccessSession(session, id)
        }
    }
}

private suspend fun RoutingContext.signUp(backend: Backend) {
    val pack = call.receive<SignUpPack>()
    val data = call.getData()
    val f = finalData(data)
    if (verify(pack.pk, pack.sig, f)) {
        DatabaseFactory.isEmpty {
            User.find {
                Users.publicKey eq pack.pk
            }
        }.mapResult { bool ->
            if (bool) {
                val ad = calcAddress(pack.pk)
                val newId = SnowflakeFactory.nextId()
                val name = backend.nameService.parse(newId)
                DatabaseFactory.query({
                    toUserInfo() to null
                }) {
                    createUser(User(null, pack.pk, ad, null, name, newId, now()))
                }.mapResult { value ->
                    saveSuccessSessionOnFirst(newId)
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
    @Suppress("KotlinConstantConditions")
    return when {
        !BuildConfig.IS_PROD && credential is CustomCredential.IdCredential && sig == credential.id.toString() -> {
            val id = credential.id
            if (DatabaseFactory.first({
                    this
                }, User::wrapRow) {
                    User.findById(id)
                }.getOrNull() != null) {
                saveSuccessSession(session, id)
                CustomPrincipal(id)
            } else {
                null
            }
        }

        sig.isNotBlank() && session.data.isNotBlank() -> {
            DatabaseFactory.first({
                this
            }, {
                it[Users.publicKey] to it[Users.id]
            }) {
                Users.select(listOf(Users.publicKey, Users.id)).where {
                    when (credential) {
                        is CustomCredential.AidCredential -> Users.aid eq credential.aid
                        is CustomCredential.IdCredential -> Users.id eq credential.id
                    }
                }
            }.getOrNull()?.let { (pubKey, id) ->
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
        DatabaseFactory.first({
            CustomPrincipal(id)
        }, User::wrapRow) {
            User.findById(id)
        }.getOrNull()
    } else {
        null
    }
}

private fun ApplicationCall.getData(): String {
    val (_, data) = getSession()
    return data
}

@OptIn(ExperimentalUuidApi::class)
private fun ApplicationCall.getSession(): Pair<UserSession, String> {
    val remote = request.origin.remoteAddress
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

fun ApplicationCall.getRateLimitKey(): Comparable<*> {
    val session = getSession().first
    return when (session) {
        is UserSession.Success -> session.id
        is UserSession.Pending -> session.remote
    }
}
