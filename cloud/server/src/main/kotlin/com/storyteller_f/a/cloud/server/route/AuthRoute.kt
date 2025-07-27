package com.storyteller_f.a.cloud.server.route

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.exposed.tables.Aids
import com.storyteller_f.a.backend.exposed.tables.User
import com.storyteller_f.a.backend.exposed.tables.Users
import com.storyteller_f.a.backend.exposed.tables.toUserInfo
import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.a.backend.service.processRawUserToUserInfo
import com.storyteller_f.a.cloud.core.service.addUserLog
import com.storyteller_f.a.cloud.server.ServerConfig
import com.storyteller_f.a.cloud.server.auth.CustomCredential
import com.storyteller_f.a.cloud.server.auth.CustomPrincipal
import com.storyteller_f.a.cloud.server.auth.UserSession
import com.storyteller_f.a.cloud.server.auth.getData
import com.storyteller_f.a.cloud.server.auth.getSession
import com.storyteller_f.a.cloud.server.auth.saveSuccessSession
import com.storyteller_f.shared.SignInPack
import com.storyteller_f.shared.SignUpPack
import com.storyteller_f.shared.calcAddress
import com.storyteller_f.shared.finalData
import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.PassType
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.filterNotNull
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.shared.verify
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.routing.RoutingContext

suspend fun RoutingContext.signIn(
    backend: Backend,
    pack: SignInPack,
    data: String
): Result<UserInfo?> {
    val f = finalData(data)
    return backend.exposedDatabase.userDatabase.getRawUserAndPublicKeyByAddress(pack.ad).filterNotNull {
        CustomBadRequestException("user not found")
    }.mapResult { (rawUser, publicKey) ->
        verify(publicKey, pack.sig, f).mapResult { isVerified ->
            if (isVerified) {
                val id = rawUser.user.id
                backend.addUserLog(id, UserLogType.SIGN_IN, id ob ObjectType.USER)
                backend.processRawUserToUserInfo(listOf(rawUser)).mapIfNotNull {
                    it.first()
                }.mapIfNotNull { value ->
                    val id = value.id
                    saveSuccessSessionOnFirst(id)
                    value
                }
            } else {
                Result.failure(BadRequestException("Verify failed"))
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

suspend fun RoutingContext.signUp(
    backend: Backend,
    pack: SignUpPack
): Result<UserInfo?> {
    val data = call.getData()
    val f = finalData(data)
    return verify(pack.pk, pack.sig, f).mapResult {
        if (it) {
            backend.exposedDatabase.userDatabase.isUserNotExistsByPublicKey(pack.pk).mapResult { userNotExists ->
                if (userNotExists) {
                    calcAddress(pack.pk).mapResult { ad ->
                        val newId = SnowflakeFactory.nextId()
                        val name = backend.nameService.parse(newId)
                        val user = User(
                            null,
                            pack.pk,
                            ad,
                            null,
                            name,
                            newId,
                            now(),
                            0,
                            PassType.RAW,
                            AlgoType.P256
                        )
                        backend.exposedDatabase.userDatabase.createUser(user).map {
                            backend.addUserLog(newId, UserLogType.SIGN_UP, newId ob ObjectType.USER)
                            saveSuccessSessionOnFirst(newId)
                            user.toUserInfo()
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
}

suspend fun Backend.getUserAuthData(
    credential: CustomCredential
): Result<Pair<String, Long>?> {
    return when (credential) {
        is CustomCredential.AidCredential -> exposedDatabase.userDatabase.getUserAuthDataByAid {
            Aids.value eq credential.aid
        }

        is CustomCredential.IdCredential -> exposedDatabase.userDatabase.getUserAuthDataBy {
            Users.id eq credential.id
        }

        is CustomCredential.AddressCredential -> exposedDatabase.userDatabase.getUserAuthDataBy {
            Users.address eq credential.ad
        }
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
            backend.exposedDatabase.userDatabase.isUserExistsByUid(id).mapIfNotNull {
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
