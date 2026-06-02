package com.storyteller_f.a.app.utils

import com.storyteller_f.a.app.CustomUserSessionManager
import com.storyteller_f.a.client.core.PendingTotpSignIn
import com.storyteller_f.a.client.core.UserAuthResult
import com.storyteller_f.a.client.core.completeSignIn
import com.storyteller_f.a.client.core.getAuthKey
import com.storyteller_f.a.client.core.getRawUserPassInfoFromAuthKey
import com.storyteller_f.a.client.core.signInTotp
import com.storyteller_f.a.client.core.startUserAuth
import com.storyteller_f.shared.model.AlgoType

class TotpRequiredException : Exception("totp required")

suspend fun CustomUserSessionManager.startAuth(
    privateKey: String,
    encryptionPrivateKey: String?,
    algo: AlgoType,
    isSignUp: Boolean
) = getAuthKey(algo, privateKey, encryptionPrivateKey).let { authKey ->
    startUserAuth(authKey, isSignUp) {
        sessionHistoryManager.addSession(getRawUserPassInfoFromAuthKey(it))
    }
}

suspend fun CustomUserSessionManager.fetchAndSaveUserInfo(
    privateKey: String,
    encryptionPrivateKey: String?,
    algo: AlgoType,
    isSignUp: Boolean
) = when (val result = startAuth(privateKey, encryptionPrivateKey, algo, isSignUp)) {
    is UserAuthResult.Success -> result.signResult.userInfo
    is UserAuthResult.RequiresTotp -> throw TotpRequiredException()
}

suspend fun CustomUserSessionManager.completeTotpSignIn(
    pending: PendingTotpSignIn,
    code: String,
) = signInTotp(code).getOrThrow().also { userInfo ->
    completeSignIn(
        pending.authKey,
        pending.data,
        pending.signature,
        pending.address,
        userInfo
    ) {
        sessionHistoryManager.addSession(getRawUserPassInfoFromAuthKey(it))
    }
}
