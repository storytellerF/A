package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.core.CustomApi
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.cloud.core.service.addAlternativeAccount
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
import com.storyteller_f.route4k.ktor.server.invoke
import com.storyteller_f.route4k.ktor.server.receiveBody
import com.storyteller_f.shared.finalData
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.UNIT_RESULT
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.verify
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions

fun Route.bindUnprotectedAccountRoute(
    backend: Backend
) {
    CustomApi.Accounts.getData(RoutingContext::handleResult) {
        Result.success(call.getData())
    }
    CustomApi.Accounts.signUp(RoutingContext::handleResult) { api ->
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

    CustomApi.Accounts.signIn(RoutingContext::handleResult) { api ->
        val mapResult = backend.signIn(call.getData(), api.receiveBody())
        mapResult.onSuccess {
            it?.id?.let { id -> saveSuccessSessionOnFirst(id) }
        }
    }
}

fun Route.bindAccountRoute() {
    CustomApi.Accounts.signOut(RoutingContext::handleResult) {
        usePrincipalOrNull { uid ->
            call.sessions.clear(UserSession::class)
            UNIT_RESULT
        }
    }
}

fun Route.bindProtectedAccountRoute(backend: Backend) {
    CustomApi.Accounts.ChildAccounts.add(RoutingContext::handleResult) {
        usePrincipal { uid ->
            backend.addAlternativeAccount(uid)
        }
    }
    CustomApi.Accounts.ChildAccounts.get(RoutingContext::handleResult) {
        usePrincipal { uid ->
            pagination(IdentifiablePagingGenerator) {
                backend.getUserAlternateUserInfoList(uid, it)
            }
        }
    }
}

fun RoutingContext.saveSuccessSessionOnFirst(id: PrimaryKey) {
    call.getSession().first.let { session ->
        if (session is UserSession.Pending) {
            call.saveSuccessSession(session, id)
        }
    }
}

suspend fun Backend.getUserAuthData(
    credential: CustomCredential
): Result<Pair<String, Long>?> {
    return when (credential) {
        is CustomCredential.AidCredential -> combinedDatabase.userDatabase.getUserAuthDataByAid(credential.aid)

        is CustomCredential.IdCredential -> combinedDatabase.userDatabase.getUserAuthDataById(credential.id)

        is CustomCredential.AddressCredential -> combinedDatabase.userDatabase.getUserAuthDataByAddress(credential.ad)
    }
}

suspend fun ApplicationCall.checkApiRequest(
    backend: Backend,
    credential: CustomCredential,
    session: UserSession.Pending
): Result<CustomPrincipal?> {
    val sig = credential.sig
    @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
    return when {
        !ServerConfig.IS_PROD && credential is CustomCredential.IdCredential && sig == credential.id.toString() -> {
            val id = credential.id
            backend.combinedDatabase.userDatabase.getRawUser(ObjectFetch.IdFetch(id)).mapIfNotNull {
                saveSuccessSession(session, id)
                CustomPrincipal(id)
            }
        }

        sig.isNotBlank() && session.data.isNotBlank() -> {
            backend.getUserAuthData(credential).mapResultIfNotNull { (pubKey, id) ->
                verify(
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
