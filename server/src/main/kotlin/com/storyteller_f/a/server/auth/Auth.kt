package com.storyteller_f.a.server.auth

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.*
import com.storyteller_f.a.server.BuildConfig
import com.storyteller_f.a.server.service.toFinalUserInfo
import com.storyteller_f.a.server.service.toUserInfo
import com.storyteller_f.shared.*
import com.storyteller_f.shared.type.OKey
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.User
import com.storyteller_f.tables.Users
import com.storyteller_f.tables.createUser
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class CustomCredential(val id: OKey, val sig: String) : Credential

private fun HttpAuthHeader.Parameterized.customCredential(): CustomCredential? {
    val id = parameters.firstOrNull {
        it.name == "id"
    }?.value?.toULong()
    val sig = parameters.firstOrNull {
        it.name == "sig"
    }?.value
    return if (id != null && sig != null) {
        CustomCredential(id, sig)
    } else {
        null
    }
}

class CustomPrincipal(val uid: OKey) : Principal

inline fun AuthenticationConfig.custom(
    name: String? = null,
    configure: CustomAuthProvider.Config.() -> Unit
) {
    val provider = CustomAuthProvider.Config(name).apply(configure).buildProvider()
    register(provider)
}

typealias CustomValidator = suspend (UserSession, ApplicationCall, CustomCredential?) -> Principal?
typealias CustomChallenge = suspend (UserSession, ApplicationCall) -> Unit

class CustomAuthProvider(private val config: Config) : AuthenticationProvider(config) {

    class Config(name: String?) : AuthenticationProvider.Config(name) {

        lateinit var validateFunction: CustomValidator
        lateinit var challengeFunction: CustomChallenge
        fun buildProvider(): CustomAuthProvider {
            return CustomAuthProvider(this)
        }

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
                } else AuthenticationFailedCause.InvalidCredentials

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

fun Application.configureAuth() {
    install(Authentication) {
        custom("custom-session") {
            validate { session, call, credential ->
                when {
                    credential != null -> call.checkApiRequest(credential, session)
                    BuildConfig.IS_PROD -> null
                    else -> checkDevWsLink(call)
                }
            }
            challenge { _, call ->
                val data = call.getData()
                call.respond(UnauthorizedResponse(HttpAuthHeader.Single("Custom", data)))
            }
        }
    }
    routing {
        authenticate("custom-session") {
            protectedContent()
        }
        authenticate("custom-session", optional = true) {
            unProtectedContent()
        }

        get("/get_data") {
            call.respondText(call.getData())
        }

        post("/sign_up") {
            signUp()
        }

        post("/sign_in") {
            signIn()
        }

        get("/ping") {
            call.respondText("pong")
        }
    }

}

private suspend fun RoutingContext.signIn() {
    val pack = call.receive<SignInPack>()
    val data = call.getData()
    val f = finalData(data)
    val userTriple = DatabaseFactory.first({ user ->
        Triple(user.toUserInfo(), user.icon, user.publicKey)
    }, User::wrapRow) {
        User.find {
            Users.address eq pack.ad
        }
    }
    if (userTriple != null) {
        val (info, icon, publicKey) = userTriple
        if (verify(publicKey, pack.sig, f)) {
            call.respond(toFinalUserInfo(info to icon))
        } else {
            call.respond(HttpStatusCode.BadRequest)
        }
    } else {
        call.respond(HttpStatusCode.BadRequest)
    }
}

private suspend fun RoutingContext.signUp() {
    val pack = call.receive<SignUpPack>()
    val data = call.getData()
    val f = finalData(data)
    if (verify(pack.pk, pack.sig, f)) {
        if (!DatabaseFactory.empty {
                User.find {
                    Users.publicKey eq pack.pk
                }
            }) {
            call.respond(HttpStatusCode.BadRequest)
        } else {
            val ad = calcAddress(pack.pk)
            val newId = SnowflakeFactory.nextId()
            val name = backend.nameService.parse(newId)
            call.respond(DatabaseFactory.query({
                it.toUserInfo() to null
            }) {
                createUser(User(null, pack.pk, ad, null, name, newId, now()))
            }.let(::toFinalUserInfo))
        }
    } else {
        call.respond(HttpStatusCode.BadRequest)
    }
}

private suspend fun ApplicationCall.checkApiRequest(
    credential: CustomCredential,
    session: UserSession
): CustomPrincipal? {
    val sig = credential.sig
    val id = credential.id
    return when (session) {
        is UserSession.Success -> {
            CustomPrincipal(session.id)
        }

        is UserSession.Pending -> {
            verifySignature(sig, id, session)
        }

    }

}

private suspend fun ApplicationCall.verifySignature(
    sig: String,
    id: OKey,
    session: UserSession.Pending
) = when {
    !BuildConfig.IS_PROD && sig == id.toString() -> {
        if (DatabaseFactory.dbQuery {
                User.findById(id) != null
            }) {
            saveSuccessSession(session, id)
            CustomPrincipal(id)
        } else {
            null
        }
    }

    sig.isNotBlank() && session.data.isNotBlank() -> {
        DatabaseFactory.first({
            it[Users.publicKey]
        }, {
            it
        }) {
            Users.select(Users.publicKey).where {
                Users.id eq id
            }
        }?.let { pubKey ->
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

private fun ApplicationCall.saveSuccessSession(
    session: UserSession.Pending,
    id: OKey
) {
    sessions.set<UserSession>(UserSession.Success(session.data, session.remote, id))
}

private suspend fun checkDevWsLink(call: ApplicationCall): CustomPrincipal? {
    val did = call.request.queryParameters["did"]
    return if (did?.all { it.isDigit() } == true) {
        val id = did.toULong()
        DatabaseFactory.queryNotNull({
            CustomPrincipal(id)
        }) {
            User.findById(id)
        }
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

