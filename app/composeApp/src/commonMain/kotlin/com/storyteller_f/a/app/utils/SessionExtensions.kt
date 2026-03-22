package com.storyteller_f.a.app.utils

import com.storyteller_f.a.app.CustomUserSessionManager
import com.storyteller_f.a.client.core.getAuthKey
import com.storyteller_f.a.client.core.getUserPass
import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.UserInfo

suspend fun CustomUserSessionManager.fetchAndSaveUserInfo(
    privateKey: String,
    encryptionPrivateKey: String?,
    algo: AlgoType,
    isSignUp: Boolean
): UserInfo {
    val authKey = getAuthKey(algo, privateKey, encryptionPrivateKey)
    return getUserPass(authKey, isSignUp) {
        sessionHistoryManager.addSession(it)
    }
}
