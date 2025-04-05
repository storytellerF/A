package com.storyteller_f.a.client_lib

import com.storyteller_f.shared.getDerPrivateKey
import com.storyteller_f.shared.model.UserInfo
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

interface LoginUserSession {
    suspend fun signature(data: String): String

    suspend fun verify(signature: String, data: String): Boolean

    suspend fun decrypt(encrypted: ByteArray, encryptedAesKey: ByteArray): String

    suspend fun address(): String
}

sealed interface ClientSession {
    data object LoginNone : ClientSession
    data object SignUpNone : ClientSession
    data class PrivateKeySignIn(val privateKey: String) :
        ClientSession

    data class PrivateKeySignUp(val privateKey: String) : ClientSession

    data class SignUpSuccess(val session: LoginUserSession) : ClientSession
}

@Serializable
data class LoginUser(
    val privateKey: String,
    val publicKey: String,
    val address: String,
)

class DefaultLoginUserSession(val loginUSer: LoginUser) : LoginUserSession {
    override suspend fun signature(data: String): String {
        return com.storyteller_f.shared.signature(loginUSer.privateKey, data)
    }

    override suspend fun verify(signature: String, data: String): Boolean {
        return com.storyteller_f.shared.verify(loginUSer.publicKey, signature, data)
    }

    override suspend fun decrypt(encrypted: ByteArray, encryptedAesKey: ByteArray): String {
        return com.storyteller_f.shared.decrypt(getDerPrivateKey(loginUSer.privateKey), encrypted, encryptedAesKey)
    }

    override suspend fun address(): String {
        return com.storyteller_f.shared.calcAddress(loginUSer.publicKey)
    }
}

object LoginViewModel {
    val state = MutableStateFlow<ClientSession>(ClientSession.LoginNone)
    val inputtedPrivateKey = state.map {
        when (it) {
            is ClientSession.PrivateKeySignIn -> it.privateKey
            is ClientSession.PrivateKeySignUp -> it.privateKey
            else -> ""
        }
    }
    val isSignUpFlow = state.map {
        it is ClientSession.PrivateKeySignUp
    }
    var session: Pair<String, String?>? = null
    val user = MutableStateFlow<UserInfo?>(null)
    val currentIsAlreadySignUp get() = state.value is ClientSession.SignUpSuccess
    val isAlreadySignUp = state.map {
        it is ClientSession.SignUpSuccess
    }
    val appStartLoginRetried = MutableStateFlow(false)
    val retryLoginState = MutableStateFlow<LoadingState?>(null)

    fun updateSession(data: String, signature: String?) {
        session = data to signature
    }

    fun updateUser(new: UserInfo) {
        user.value = new
    }

    fun updatePrivateKey(pemPrivateKey: String) {
        Napier.v("$pemPrivateKey ${state.value}", tag = "ClientAuth")
        when (state.value) {
            is ClientSession.PrivateKeySignIn -> {
                state.value = ClientSession.PrivateKeySignIn(pemPrivateKey)
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

    fun signOut() {
        state.value = ClientSession.LoginNone
        user.value = null
        session = null
    }
}
