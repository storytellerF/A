package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.api.CustomApi.Accounts.ChildAccounts.AddChildAccountRequest
import com.storyteller_f.a.api.SignInBody
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.UserAuthData
import com.storyteller_f.a.cloud.core.service.addChildAccount
import com.storyteller_f.a.cloud.core.service.addUserLog
import com.storyteller_f.a.cloud.core.service.getUserAlternateUserInfoList
import com.storyteller_f.a.cloud.core.service.signIn
import com.storyteller_f.a.cloud.core.service.signUp
import com.storyteller_f.a.cloud.server.ServerConfig
import com.storyteller_f.a.cloud.server.auth.CustomCredential
import com.storyteller_f.a.cloud.server.auth.CustomPrincipal
import com.storyteller_f.a.cloud.server.auth.UserSession
import com.storyteller_f.a.cloud.server.auth.getData
import com.storyteller_f.a.cloud.server.auth.getSession
import com.storyteller_f.a.cloud.server.auth.handleResult
import com.storyteller_f.a.cloud.server.auth.saveSuccessSession
import com.storyteller_f.a.cloud.server.auth.usePrincipal
import com.storyteller_f.a.cloud.server.auth.usePrincipalOrNull
import com.storyteller_f.a.cloud.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.cloud.server.common.pagination
import com.storyteller_f.endpoint4k.ktor.server.invoke
import com.storyteller_f.endpoint4k.ktor.server.receiveBody
import com.storyteller_f.shared.finalData
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.UNIT_RESULT
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResultIfNotNull
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions

fun Route.bindUnprotectedAccountRoute(
    backend: Backend
) {
    CustomApi.Accounts.getData(handleResult()) {
        Result.success(call.getData())
    }
    CustomApi.Accounts.signUp(handleResult()) { api ->
        if (backend.customConfig.buildType == "prod") {
            Result.failure(Exception("not support"))
        } else {
            val data = call.getData()
            val mapResult = backend.signUp(data, api.receiveBody())
            mapResult.onSuccess {
                val newId = it.id
                backend.addUserLog(newId, UserLogType.SIGN_UP, newId ob ObjectType.USER)
                saveSuccessSessionOnFirst(newId)
            }
        }
    }

    CustomApi.Accounts.signIn(handleResult()) { api ->
        backend.signIn(call.getData(), api.receiveBody<UserInfo, SignInBody>()).onSuccess {
            saveSuccessSessionOnFirst(it.id)
        }
    }
}

fun Route.bindAccountRoute() {
    CustomApi.Accounts.signOut(handleResult()) {
        usePrincipalOrNull {
            call.sessions.clear(UserSession::class)
            UNIT_RESULT
        }
    }
}

fun Route.bindProtectedAccountRoute(backend: Backend) {
    CustomApi.Accounts.ChildAccounts.add(handleResult()) { api ->
        usePrincipal { uid ->
            val request: AddChildAccountRequest = api.receiveBody()
            backend.addChildAccount(
                uid,
                request.encryptedPrivateKey,
                request.encryptedAesKey,
                request.derPublicKey,
                request.algoType,
                request.encryptedEncryptionPrivateKey,
                request.encryptionPublicKey
            )
        }
    }
    CustomApi.Accounts.ChildAccounts.get(handleResult()) { q ->
        usePrincipal { uid ->
            q.pagination(IdentifiablePagingGenerator) {
                backend.getUserAlternateUserInfoList(uid, it)
            }
        }
    }
}

fun RoutingContext.saveSuccessSessionOnFirst(id: PrimaryKey) {
    val session = call.getSession()
    if (session is UserSession.Pending) {
        call.saveSuccessSession(session, id)
    }
}

suspend fun Backend.getUserAuthData(
    credential: CustomCredential
): Result<UserAuthData?> {
    val userDatabase = database.user
    return when (credential) {
        is CustomCredential.AidCredential -> userDatabase.getUserAuthDataByAid(credential.aid)

        is CustomCredential.IdCredential -> userDatabase.getUserAuthDataById(credential.id)

        is CustomCredential.AddressCredential -> userDatabase.getUserAuthDataByAddress(credential.ad)
    }
}

suspend fun ApplicationCall.checkApiRequest(
    backend: Backend,
    credential: CustomCredential,
    session: UserSession.Pending
): Result<CustomPrincipal?> {
    if (session.label != "user") return Result.success(null)
    val sig = credential.sig
    @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
    return when {
        !ServerConfig.IS_PROD && credential is CustomCredential.IdCredential && sig == credential.id.toString() -> {
            val id = credential.id
            backend.database.user.getRawUser(ObjectFetch.IdFetch(id)).mapIfNotNull {
                saveSuccessSession(session, id)
                CustomPrincipal(id)
            }
        }

        sig.isNotBlank() && session.data.isNotBlank() -> {
            backend.getUserAuthData(credential).mapResultIfNotNull { (pubKey, id, algo) ->
                getAlgo(algo).verify(
                    pubKey,
                    sig,
                    finalData(session.data)
                ).mapIfNotNull {
                    saveSuccessSession(session, id)
                    CustomPrincipal(id)
                }
            }
        }

        else -> {
            Result.success(null)
        }
    }
}

suspend fun Backend.getAdminAuthData(
    credential: CustomCredential
): Result<UserAuthData?> {
    return when (credential) {
        is CustomCredential.IdCredential -> database.panelAccount.getUserAuthDataById(credential.id)

        is CustomCredential.AddressCredential -> database.panelAccount.getUserAuthDataByAddress(credential.ad)

        else -> Result.failure(ForbiddenException())
    }
}

suspend fun ApplicationCall.checkAdminApiRequest(
    backend: Backend,
    credential: CustomCredential,
    session: UserSession.Pending
): Result<CustomPrincipal?> {
    if (session.label != "panel") return Result.success(null)
    val sig = credential.sig
    if (!sig.isNotBlank() || !session.data.isNotBlank()) {
        return Result.success(null)
    }
    return backend.getAdminAuthData(credential).mapResultIfNotNull { (pubKey, id, algo) ->
        getAlgo(algo).verify(
            pubKey,
            sig,
            finalData(session.data)
        ).mapIfNotNull {
            saveSuccessSession(session, id)
            CustomPrincipal(id)
        }
    }
}
