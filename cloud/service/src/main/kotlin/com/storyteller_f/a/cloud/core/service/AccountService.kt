package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.types.PanelAccount
import com.storyteller_f.a.backend.core.types.User
import com.storyteller_f.a.backend.core.types.toUserInfo
import com.storyteller_f.shared.SignInPack
import com.storyteller_f.shared.SignUpPack
import com.storyteller_f.shared.calcAddress
import com.storyteller_f.shared.finalData
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
import com.storyteller_f.shared.verify

suspend fun Backend.signUp(
    data: String,
    pack: SignUpPack
): Result<UserInfo> {
    val f = finalData(data)
    return verify(pack.pk, pack.sig, f).errorIfFalse {
        CustomBadRequestException("Verify failed")
    }.mapResult {
        combinedDatabase.userDatabase.isUserNotExistsByPublicKey(pack.pk).errorIfFalse {
            CustomBadRequestException("User exists")
        }
    }.mapResult {
        calcAddress(pack.pk)
    }.mapResult { ad ->
        val newId = SnowflakeFactory.nextId()
        val name = nameService.parse(newId)
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
        combinedDatabase.userDatabase.createUser(user).map {
            user.toUserInfo()
        }
    }
}

suspend fun Backend.signIn(
    data: String,
    pack: SignInPack
): Result<UserInfo> {
    val f = finalData(data)
    return combinedDatabase.userDatabase.getRawUserAndPublicKeyByAddress(pack.ad)
        .filterNotNull {
            CustomBadRequestException("user not found")
        }.mapResult { (rawUser, publicKey) ->
            verify(publicKey, pack.sig, f).mapResult { isVerified ->
                if (isVerified) {
                    Result.success(rawUser)
                } else {
                    Result.failure(CustomBadRequestException("Verify failed"))
                }
            }
        }.mapResult { rawUser ->
            val id = rawUser.user.id
            addUserLog(id, UserLogType.SIGN_IN, id ob ObjectType.USER)
            processRawUserToUserInfo(listOf(rawUser)).map {
                it.first()
            }
        }
}

suspend fun Backend.adminSignIn(data: String, pack: SignInPack): Result<PanelAccountInfo> {
    val f = finalData(data)
    return combinedDatabase.panelAccountDatabase.getRawUserAndPublicKeyByAddress(pack.ad)
        .filterNotNull {
            CustomBadRequestException("user not found")
        }.mapResult { (rawPanelAccount, publicKey) ->
            verify(publicKey, pack.sig, f).mapResult { isVerified ->
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
    pack: SignUpPack
): Result<PanelAccountInfo> {
    val f = finalData(data)
    return verify(pack.pk, pack.sig, f).errorIfFalse {
        CustomBadRequestException("Verify failed")
    }.mapResult {
        combinedDatabase.panelAccountDatabase.isUserNotExistsByPublicKey(pack.pk).errorIfFalse {
            CustomBadRequestException("User exists")
        }
    }.mapResult {
        calcAddress(pack.pk)
    }.mapResult { ad ->
        val newId = SnowflakeFactory.nextId()
        val name = nameService.parse(newId)
        val user = PanelAccount(
            newId,
            name,
            PassType.RAW,
            AlgoType.P256,
            pack.pk,
            ad,
        )
        combinedDatabase.panelAccountDatabase.addPanelAccount(user).map {
            PanelAccountInfo(newId, name)
        }
    }
}
