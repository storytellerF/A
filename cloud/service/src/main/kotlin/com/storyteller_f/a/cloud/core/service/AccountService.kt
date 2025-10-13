package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.types.User
import com.storyteller_f.a.backend.core.types.toUserInfo
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
import com.storyteller_f.shared.utils.filterNotNull
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.now
import com.storyteller_f.shared.verify

suspend fun Backend.signUp(
    data: String,
    pack: SignUpPack
): Result<UserInfo> {
    val f = finalData(data)
    return verify(pack.pk, pack.sig, f).mapResult {
        if (it) {
            combinedDatabase.userDatabase.isUserNotExistsByPublicKey(pack.pk)
                .mapResult { userNotExists ->
                    if (userNotExists) {
                        calcAddress(pack.pk).mapResult { ad ->
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
                    } else {
                        Result.failure(CustomBadRequestException("User exists"))
                    }
                }
        } else {
            Result.failure(CustomBadRequestException("Verify failed"))
        }
    }
}

suspend fun Backend.signIn(
    data: String,
    pack: SignInPack
): Result<UserInfo?> {
    val f = finalData(data)
    return combinedDatabase.userDatabase.getRawUserAndPublicKeyByAddress(pack.ad)
        .filterNotNull {
            CustomBadRequestException("user not found")
        }.mapResult { (rawUser, publicKey) ->
            verify(publicKey, pack.sig, f).mapResult { isVerified ->
                if (isVerified) {
                    val id = rawUser.user.id
                    addUserLog(id, UserLogType.SIGN_IN, id ob ObjectType.USER)
                    processRawUserToUserInfo(listOf(rawUser)).mapIfNotNull {
                        it.first()
                    }
                } else {
                    Result.failure(CustomBadRequestException("Verify failed"))
                }
            }
        }
}
