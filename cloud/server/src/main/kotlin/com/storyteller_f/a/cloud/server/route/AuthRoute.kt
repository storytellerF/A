/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.ChildAccountInfoListResponse
import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.api.CustomApi.Accounts.ChildAccounts.AddChildAccountRequest
import com.storyteller_f.a.api.TotpCodeBody
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.cloud.core.service.addChildAccount
import com.storyteller_f.a.cloud.core.service.addUserLog
import com.storyteller_f.a.cloud.core.service.getUserAlternateUserInfoList
import com.storyteller_f.a.cloud.core.service.getUserInfo
import com.storyteller_f.a.cloud.core.service.signIn
import com.storyteller_f.a.cloud.core.service.signUp
import com.storyteller_f.a.cloud.core.service.verifyUserTotp
import com.storyteller_f.a.cloud.server.auth.UserSession
import com.storyteller_f.a.cloud.server.auth.beginSigninChallenge
import com.storyteller_f.a.cloud.server.auth.clearAllSigninSessions
import com.storyteller_f.a.cloud.server.auth.clearSigninSession
import com.storyteller_f.a.cloud.server.auth.consumeSigninChallenge
import com.storyteller_f.a.cloud.server.auth.getData
import com.storyteller_f.a.cloud.server.auth.getSession
import com.storyteller_f.a.cloud.server.auth.handleResult
import com.storyteller_f.a.cloud.server.auth.isSigninSessionValid
import com.storyteller_f.a.cloud.server.auth.saveSuccessSession
import com.storyteller_f.a.cloud.server.auth.saveTwoFactorPendingSession
import com.storyteller_f.a.cloud.server.auth.usePrincipal
import com.storyteller_f.a.cloud.server.auth.usePrincipalOrNull
import com.storyteller_f.a.cloud.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.cloud.server.common.pagination
import com.storyteller_f.endpoint4k.ktor.server.invoke
import com.storyteller_f.endpoint4k.ktor.server.receiveBody
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.UNIT_RESULT
import com.storyteller_f.shared.utils.mapResult
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext

fun Route.bindUnprotectedAccountRoute(backend: Backend) {
    CustomApi.Accounts.getData(handleResult(backend)) {
        call.beginSigninChallenge()
    }
    CustomApi.Accounts.signUp(handleResult(backend)) { api ->
        if (!backend.customConfig.enableSignUp) {
            Result.failure(Exception("not support"))
        } else {
            call.consumeSigninChallenge()
            val data = call.getData()
            val mapResult = backend.signUp(data, api.receiveBody())
            mapResult.onSuccess {
                val newId = it.id
                backend.addUserLog(newId, UserLogType.SIGN_UP, newId ob ObjectType.USER)
                saveSuccessSessionOnFirst(newId)
            }
        }
    }

    CustomApi.Accounts.signIn(handleResult(backend)) { api ->
        call.consumeSigninChallenge()
        backend.signIn(call.getData(), api.receiveBody()).map { signIn ->
            when (signIn) {
                is com.storyteller_f.a.cloud.core.service.SignInServiceResponse.Success -> {
                    saveSuccessSessionOnFirst(signIn.uid)
                }

                is com.storyteller_f.a.cloud.core.service.SignInServiceResponse.RequiresTotp -> {
                    saveTwoFactorPendingSession(signIn.uid)
                }
            }
            signIn.response
        }
    }
    CustomApi.Accounts.signInTotp(handleResult(backend)) { api ->
        val body: TotpCodeBody = api.receiveBody()
        when (val session = call.getSession()) {
            is UserSession.TwoFactorPending -> {
                backend.verifyUserTotp(session.id, body.code).mapResult { verified ->
                    if (verified) {
                        backend.addUserLog(session.id, UserLogType.SIGN_IN, session.id ob ObjectType.USER)
                        call.saveSuccessSession(session)
                        backend.getUserInfo(ObjectFetch.IdFetch(session.id))
                    } else {
                        Result.failure(CustomBadRequestException("invalid totp code"))
                    }
                }
            }

            else -> Result.failure(ForbiddenException())
        }
    }
}

fun Route.bindAccountRoute(backend: Backend) {
    CustomApi.Accounts.signOut(handleResult(backend)) {
        usePrincipalOrNull {
            call.clearSigninSession()
            UNIT_RESULT
        }
    }
}

fun Route.bindProtectedAccountRoute(backend: Backend) {
    CustomApi.Accounts.activity(handleResult(backend)) {
        if (call.isSigninSessionValid(
                active = true,
            )
        ) {
            UNIT_RESULT
        } else {
            Result.failure(com.storyteller_f.a.backend.core.UnauthorizedException())
        }
    }

    CustomApi.Accounts.signOutAll(handleResult(backend)) {
        call.clearAllSigninSessions()
        UNIT_RESULT
    }

    CustomApi.Accounts.session(handleResult(backend)) {
        usePrincipal { uid -> backend.getUserInfo(ObjectFetch.IdFetch(uid)) }
    }

    CustomApi.Accounts.ChildAccounts.add(handleResult(backend)) { api ->
        usePrincipal { uid ->
            val request: AddChildAccountRequest = api.receiveBody()
            backend.addChildAccount(uid, request)
        }
    }
    CustomApi.Accounts.ChildAccounts.get(handleResult(backend)) { q ->
        usePrincipal { uid ->
            q.pagination(IdentifiablePagingGenerator, { l, p ->
                ChildAccountInfoListResponse(l, p)
            }) {
                backend.getUserAlternateUserInfoList(uid, it)
            }
        }
    }
}

suspend fun RoutingContext.saveSuccessSessionOnFirst(id: PrimaryKey) {
    val session = call.getSession()
    val pending =
        when (session) {
            is UserSession.Pending -> session
            is UserSession.Success -> return
            is UserSession.TwoFactorPending -> return
        }
    call.saveSuccessSession(pending, id)
}

suspend fun RoutingContext.saveTwoFactorPendingSession(id: PrimaryKey) {
    val session = call.getSession()
    if (session is UserSession.Pending) {
        call.saveTwoFactorPendingSession(session, id)
    }
}
