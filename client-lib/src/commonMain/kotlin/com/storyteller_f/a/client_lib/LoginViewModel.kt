package com.storyteller_f.a.client_lib

import com.storyteller_f.shared.model.UserInfo
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

sealed interface ClientSession {
    data object LoginNone : ClientSession
    data object SignUpNone : ClientSession
    data class PrivateKeyLogin(val privateKey: String) :
        ClientSession

    data class PrivateKeySignUp(val privateKey: String) : ClientSession

    data class LoginSuccess(val privateKey: String, val publicKey: String, val address: String) : ClientSession
}

object LoginViewModel {
    val state = MutableStateFlow<ClientSession>(ClientSession.LoginNone)
    val privateKey = state.map {
        when (it) {
            is ClientSession.PrivateKeyLogin -> it.privateKey

            is ClientSession.PrivateKeySignUp -> it.privateKey

            else -> ""
        }
    }
    val isSignUp = state.map {
        it is ClientSession.PrivateKeySignUp
    }
    var session: Pair<String, String?>? = null
    val user = MutableStateFlow<UserInfo?>(null)

    fun updateSession(new: String, signature: String?) {
        session = Pair(new, signature)
    }

    fun updateUser(new: UserInfo) {
        user.value = new
    }

    fun updatePrivateKey(pemPrivateKey: String) {
        Napier.v("$pemPrivateKey ${state.value}", tag = "ClientAuth")
        when (state.value) {
            is ClientSession.PrivateKeyLogin -> {
                state.value = ClientSession.PrivateKeyLogin(pemPrivateKey)
            }

            is ClientSession.PrivateKeySignUp -> {
                state.value = ClientSession.PrivateKeySignUp(pemPrivateKey)
            }

            else -> {

            }
        }
    }

    fun updateState(newState: ClientSession) {
        state.value = newState
    }

}
