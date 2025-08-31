package com.storyteller_f.a.client.core

import com.storyteller_f.shared.calcAddress
import com.storyteller_f.shared.finalData
import com.storyteller_f.shared.getDerPublicKeyFromPrivateKey
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.signature
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.checkTsIsValid
import com.storyteller_f.shared.utils.merge
import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.ExperimentalTime

interface SessionModel {
    val uid: PrimaryKey?
    val dataAndSignature: Pair<String, String?>?
    val currentUserPass: UserPass?
    val state: StateFlow<ClientSessionState>
    val userHandler: FixedLoadingHandler<UserInfo?>
    fun updateSignature(data: String, signature: String?)
    fun generateData(): String
    suspend fun clear()
    fun updateState(newState: ClientSessionState)
    suspend fun updateUser(new: UserInfo)
}

interface SessionManager {
    val client: HttpClient
    val webSocketClient: WebSocketClient
    val sessionModel: SessionModel
    val isAlreadySignUp: StateFlow<Boolean>
    val address: StateFlow<String?>

    val currentIsAlreadySignUp: Boolean get() = isAlreadySignUp.value

    suspend fun login() {
        val userPass = sessionModel.currentUserPass ?: return
        val userHandler = sessionModel.userHandler
        userHandler.request({
            userHandler.done(it)
        }) {
            runCatching {
                val (data, address) = merge({
                    getData()
                }, {
                    userPass.address()
                }).getOrThrow()
                val signature = userPass.signature(finalData(data)).getOrThrow()
                val userInfo = signIn(address, signature).getOrThrow()
                sessionModel.updateSignature(data, signature)
                userInfo
            }
        }
    }
}

class UserSessionManager(
    override val client: HttpClient,
    override val webSocketClient: WebSocketClientImpl,
    override val sessionModel: SessionModel,
) : SessionManager {
    override val isAlreadySignUp = MutableStateFlow(false)
    override val address = MutableStateFlow<String?>(null)
}

fun createUserSessionManager(
    webSocketUrl: String,
    createClient: (UserSessionModel, CookiesStorage) -> HttpClient,
    onReceiveFrame: suspend (RoomFrame, UserSessionModel) -> Unit,
): UserSessionManager {
    val cookieManager = AcceptAllCookiesStorage()
    val model = UserSessionModel()
    val client = createClient(model, cookieManager)
    val webSocketClient = WebSocketClientImpl(
        model,
        { userInfo, sig ->
            client.webSocketSession(webSocketUrl) {
                addRequestHeaders(userInfo, sig)
            }
        },
    ) {
        onReceiveFrame(it, model)
    }
    return UserSessionManager(client, webSocketClient, model)
}

class UserSessionModel : SessionModel {
    override val state = MutableStateFlow<ClientSessionState>(ClientSessionState.None)

    // 用于header 和server 协商被签名的数据
    private var currentStamp = 0L
    override val uid: PrimaryKey?
        get() = userHandler.data.value?.id

    // currentData 是本地使用的，但是还是需要依据server 的为准
    override var dataAndSignature: Pair<String, String?>? = null
    override val currentUserPass: UserPass?
        get() = (state.value as? ClientSessionState.Success)?.session
    override val userHandler = FixedLoadingHandler<UserInfo?>()

    @OptIn(ExperimentalTime::class)
    override fun generateData(): String {
        val (nowSeconds, isValid) = checkTsIsValid(currentStamp, 60 * 3)
        return if (isValid) {
            currentStamp
        } else {
            // 超时，需要替换新的
            currentStamp = nowSeconds
            nowSeconds
        }.toString()
    }

    override fun updateSignature(data: String, signature: String?) {
        dataAndSignature = data to signature
    }

    override suspend fun updateUser(new: UserInfo) {
        userHandler.done(new)
    }

    override fun updateState(newState: ClientSessionState) {
        state.value = newState
    }

    override suspend fun clear() {
        state.value = ClientSessionState.None
        userHandler.done(null)
        dataAndSignature = null
    }
}

suspend fun SessionManager.signUpOrInFromPrivateKey(
    pemPrivateKey: String,
    isSignUp: Boolean,
    buildUserPass: suspend (RawUserPassInfo) -> UserPass
): UserInfo {
    val publicKey = getDerPublicKeyFromPrivateKey(pemPrivateKey).getOrThrow()
    val data = getData().getOrThrow()
    val f = finalData(data)
    val sig = signature(pemPrivateKey, f).getOrThrow()
    val ad = calcAddress(publicKey).getOrThrow()
    val u = when {
        isSignUp -> signUp(publicKey, sig)
        else -> signIn(ad, sig)
    }.getOrThrow()
    sessionModel.updateUser(u)
    sessionModel.updateSignature(data, sig)
    sessionModel.updateState(
        ClientSessionState.Success(
            buildUserPass(
                RawUserPassInfo(
                    pemPrivateKey,
                    publicKey,
                    ad
                )
            )
        )
    )
    return u
}
