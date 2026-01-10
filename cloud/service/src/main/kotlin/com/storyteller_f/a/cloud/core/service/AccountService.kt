package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.api.SignInBody
import com.storyteller_f.a.api.SignUpBody
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.types.PanelAccount
import com.storyteller_f.a.backend.core.types.User
import com.storyteller_f.a.backend.core.types.toUserInfo
import com.storyteller_f.shared.Algo
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
    // Simple heuristic: Dilithium keys are much larger than P256 keys.
    // P256 PEM is ~200-300 chars. Dilithium PEM is > 2000 chars.
    val algoType = if (pack.publicKey.length > 1000) AlgoType.DILITHIUM else AlgoType.P256
    val algo = getAlgo(algoType)
    return runCatching {
        signUpInternal(algo, pack, f, algoType).toUserInfo()
    }
}

private suspend fun Backend.signUpInternal(
    algo: Algo,
    pack: SignUpBody,
    f: String,
    algoType: AlgoType
): User {
    val verify = algo.verify(pack.publicKey, pack.signature, f).getOrThrow()
    if (!verify) {
        throw CustomBadRequestException("Verify failed")
    }
    if (!database.user.isUserNotExistsByPublicKey(pack.publicKey).getOrThrow()) {
        throw CustomBadRequestException("User exists")
    }
    val ad = algo.calcAddress(pack.publicKey).getOrThrow()
    val newId = SnowflakeFactory.nextId()
    val notificationId = SnowflakeFactory.nextId()
    val name = nameService.parse(newId)

    val (encPubKey, encPrivKey) = if (algoType == AlgoType.DILITHIUM) {
        val (priKey, pubKey) = algo.generateEncryptionPemKeyPair().getOrThrow()
        val derPriKey = algo.getDerPrivateKey(priKey).getOrThrow()
        val derPubKey = algo.getDerPublicKeyFromPem(pubKey).getOrThrow()
        derPubKey to derPriKey
    } else {
        null to null
    }

    val user = User(
        null,
        encPubKey,
        encPrivKey,
        pack.publicKey,
        ad,
        null,
        name,
        newId,
        now(),
        0,
        PassType.RAW,
        algoType,
        notificationId
    )
    return database.user.createUser(user).getOrThrow()
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
            getAlgo(rawUser.user.algoType).verify(publicKey, pack.signature, f).mapResult { isVerified ->
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
            val user = PanelAccount(newId, name, PassType.RAW, AlgoType.P256, pack.publicKey, ad)
            database.panelAccount.addPanelAccount(user).map {
                PanelAccountInfo(newId, name)
            }
        }
    }
}
