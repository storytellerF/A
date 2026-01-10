package com.storyteller_f.a.app.utils

import com.storyteller_f.a.app.CustomUserSessionManager
import com.storyteller_f.a.client.core.getUserPass
import com.storyteller_f.shared.model.AlgoType

suspend fun CustomUserSessionManager.fetchAndSaveUserInfo(
    privateKey: String,
    algo: AlgoType,
    isSignUp: Boolean
) {
    getUserPass(
        privateKey,
        algo,
        isSignUp
    ) {
        sessionHistoryManager.addSession(it)
    }
}
