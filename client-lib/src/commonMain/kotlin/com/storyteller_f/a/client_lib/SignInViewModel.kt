package com.storyteller_f.a.client_lib

import com.storyteller_f.shared.calcAddress
import com.storyteller_f.shared.getDerPrivateKey
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.utils.checkTsIsValid
import com.storyteller_f.shared.utils.mapResult
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable

interface LoginUserSession {
    suspend fun signature(data: String): Result<String>

    suspend fun verify(signature: String, data: String): Result<Boolean>

    suspend fun decrypt(encrypted: ByteArray, encryptedAesKey: ByteArray): Result<String>

    suspend fun address(): Result<String>
}

sealed interface ClientSession {
    data object SignInNone : ClientSession
    data class SignInSuccess(val session: LoginUserSession) : ClientSession
}

@Serializable
data class LoginUser(
    val privateKey: String,
    val publicKey: String,
    val address: String,
)

class DefaultLoginUserSession(val loginUSer: LoginUser) : LoginUserSession {
    override suspend fun signature(data: String): Result<String> {
        return com.storyteller_f.shared.signature(loginUSer.privateKey, data)
    }

    override suspend fun verify(signature: String, data: String): Result<Boolean> {
        return com.storyteller_f.shared.verify(loginUSer.publicKey, signature, data)
    }

    override suspend fun decrypt(encrypted: ByteArray, encryptedAesKey: ByteArray): Result<String> {
        return getDerPrivateKey(loginUSer.privateKey).mapResult { derPrivateKeyStr ->
            com.storyteller_f.shared.decryptMessage(
                derPrivateKeyStr,
                encrypted,
                encryptedAesKey
            )
        }
    }

    override suspend fun address(): Result<String> {
        return calcAddress(loginUSer.publicKey)
    }
}

object SignInViewModel {
    val state = MutableStateFlow<ClientSession>(ClientSession.SignInNone)

    // 用于header 和server 协商被签名的数据
    private var currentStamp = 0L
    val currentData: Long get() {
        val (nowSeconds, isValid) = checkTsIsValid(currentStamp, 60 * 3)
        return if (isValid) {
            currentStamp
        } else {
            // 超时，需要替换新的
            currentStamp = nowSeconds
            nowSeconds
        }
    }

    // currentData 是本地使用的，但是还是需要依据server 的为准
    var session: Pair<String, String?>? = null
    val user = MutableStateFlow<UserInfo?>(null)
    val currentIsAlreadySignUp get() = state.value is ClientSession.SignInSuccess

    @OptIn(DelicateCoroutinesApi::class)
    val isAlreadySignUp = state.map {
        it is ClientSession.SignInSuccess
    }.stateIn(GlobalScope, SharingStarted.Eagerly, false)
    val appStartLoginRetried = MutableStateFlow(false)
    val retryLoginState = MutableStateFlow<LoadingState?>(null)

    fun updateSession(data: String, signature: String?) {
        session = data to signature
    }

    fun updateUser(new: UserInfo) {
        user.value = new
    }

    fun updateState(newState: ClientSession) {
        state.value = newState
    }

    fun signOut() {
        state.value = ClientSession.SignInNone
        user.value = null
        session = null
    }
}
