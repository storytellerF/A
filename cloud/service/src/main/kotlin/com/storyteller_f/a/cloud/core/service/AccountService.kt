package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.api.core.SignInBody
import com.storyteller_f.a.api.core.SignUpBody
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.types.PanelAccount
import com.storyteller_f.a.backend.core.types.User
import com.storyteller_f.a.backend.core.types.toUserInfo
import com.storyteller_f.shared.finalData
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.PanelAccountInfo
import com.storyteller_f.shared.model.PassType
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.errorIfFalse
import com.storyteller_f.shared.utils.filterNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.now

suspend fun Backend.signUp(
    data: String,
    pack: SignUpBody
): Result<UserInfo> {
    val f = finalData(data)
    return getAlgo().run {
        verify(pack.publicKey, pack.signature, f).errorIfFalse {
            CustomBadRequestException("Verify failed")
        }.mapResult {
            database.user.isUserNotExistsByPublicKey(pack.publicKey).errorIfFalse {
                CustomBadRequestException("User exists")
            }
        }.mapResult {
            calcAddress(pack.publicKey)
        }.mapResult { ad ->
            val newId = SnowflakeFactory.nextId()
            val notificationId = SnowflakeFactory.nextId()
            val name = nameService.parse(newId)
            val user = User(
                null,
                pack.publicKey,
                ad,
                null,
                name,
                newId,
                now(),
                0,
                PassType.RAW,
                AlgoType.P256,
                notificationId
            )
            database.user.createUser(user)
        }
    }.map {
        it.toUserInfo()
    }
}

suspend fun Backend.signIn(
    data: String,
    pack: SignInBody
): Result<UserInfo> {
    val f = finalData(data)
    return database.user.getRawUserAndPublicKeyByAddress(pack.address)
        .filterNotNull {
            CustomBadRequestException("user not found")
        }.mapResult { (rawUser, publicKey) ->
            getAlgo().verify(publicKey, pack.signature, f).mapResult { isVerified ->
                if (isVerified) {
                    Result.success(rawUser)
                } else {
                    Result.failure(CustomBadRequestException("Verify failed"))
                }
            }
        }.mapResult { rawUser ->
            val id = rawUser.user.id
            addUserLog(id, UserLogType.SIGN_IN, id ob ObjectType.USER)
            processRawUserToUserInfo(listOf(rawUser))
        }.map {
            it.first()
        }
}

suspend fun Backend.adminSignIn(data: String, pack: SignInBody): Result<PanelAccountInfo> {
    val f = finalData(data)
    return database.panelAccount.getRawUserAndPublicKeyByAddress(pack.address)
        .filterNotNull {
            CustomBadRequestException("user not found")
        }.mapResult { (rawPanelAccount, publicKey) ->
            getAlgo().verify(publicKey, pack.signature, f).mapResult { isVerified ->
                if (isVerified) {
                    Result.success(rawPanelAccount)
                } else {
                    Result.failure(CustomBadRequestException("Verify failed"))
                }
            }
        }.map { rawPanelAccount ->
            val id = rawPanelAccount.id
            PanelAccountInfo(id, rawPanelAccount.name)
        }
}

suspend fun Backend.adminSignUp(
    data: String,
    pack: SignUpBody
): Result<PanelAccountInfo> {
    val f = finalData(data)
    return getAlgo().run {
        verify(pack.publicKey, pack.signature, f).errorIfFalse {
            CustomBadRequestException("Verify failed")
        }.mapResult {
            database.panelAccount.isUserNotExistsByPublicKey(pack.publicKey).errorIfFalse {
                CustomBadRequestException("User exists")
            }
        }.mapResult {
            calcAddress(pack.publicKey)
        }.mapResult { ad ->
            val newId = SnowflakeFactory.nextId()
            val name = nameService.parse(newId)
            val user = PanelAccount(
                newId,
                name,
                PassType.RAW,
                AlgoType.P256,
                pack.publicKey,
                ad,
            )
            database.panelAccount.addPanelAccount(user).map {
                PanelAccountInfo(newId, name)
            }
        }
    }
}
