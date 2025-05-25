package com.storyteller_f.a.client_lib

import com.storyteller_f.shared.calcAddress
import com.storyteller_f.shared.finalData
import com.storyteller_f.shared.getDerPublicKeyFromPrivateKey
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.signature
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.checkTsIsValid
import com.storyteller_f.shared.utils.mapResult
import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

interface SessionModel {
    val uid: PrimaryKey?
    val session: Pair<String, String?>?
    val currentUserPass: UserPass?
    val state: StateFlow<ClientSessionState>
    val userHandler: FixedLoadingHandler<UserInfo?>
    fun updateSignature(data: String, signature: String?)
    fun generateData(): String
    fun clear()
    fun updateState(newState: ClientSessionState)
    fun updateUser(new: UserInfo)

    fun isAlreadyLogin(): Boolean {
        return currentUserPass != null
    }
}

interface SessionManager {
    val client: HttpClient
    val webSocketClient: WebSocketClient
    val sessionModel: SessionModel

    val currentIsAlreadySignUp: Boolean get() = sessionModel.isAlreadyLogin()
    val isAlreadySignUp: StateFlow<Boolean>

    suspend fun login()
}

class UserSessionManager(
    override val client: HttpClient,
    override val webSocketClient: WebSocketClientImpl,
    override val sessionModel: SessionModel,
) :
    SessionManager {
    override val isAlreadySignUp = MutableStateFlow<Boolean>(false)
    override suspend fun login() {
        val userHandler = sessionModel.userHandler
        val userPass = sessionModel.currentUserPass ?: return
        userHandler.request {
            getData().mapResult { data ->
                userPass.address().mapResult { address ->
                    userPass.signature(finalData(data)).mapResult { signature ->
                        signIn(address, signature).map {
                            sessionModel.updateSignature(data, signature)
                            it
                        }
                    }
                }
            }
        }
    }

    fun CoroutineScope.start(): List<Job> {
        return listOf(launch {
            listenerUserInfo()
        }, launch {
            listenerState()
        }, launch {
            listenerWebsocket()
        })
    }

    suspend fun listenerUserInfo() {
        val model = sessionModel
        combine(model.state, model.userHandler.data) { t1, t2 ->
            t1 to t2
        }.distinctUntilChanged().collect { (state, userInfo) ->
            if (state is ClientSessionState.Success && userInfo == null) {
                login()
            }
        }
    }

    suspend fun listenerState() {
        sessionModel.state.map {
            it is ClientSessionState.Success
        }.collect {
            isAlreadySignUp.value = it
        }
    }

    suspend fun listenerWebsocket() {
        webSocketClient.start()
    }
}

fun createUserSessionManager(
    webSocketUrl: String,
    createClient: (UserSessionModel, CookiesStorage) -> HttpClient,
    onReceiveFrame: suspend (RoomFrame, UserSessionModel) -> Unit
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
        get() = user.value?.id

    // currentData 是本地使用的，但是还是需要依据server 的为准
    override var session: Pair<String, String?>? = null
    override val currentUserPass: UserPass?
        get() = (state.value as? ClientSessionState.Success)?.session
    override val userHandler = FixedLoadingHandler<UserInfo?>()
    val user get() = userHandler.data

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
        session = data to signature
    }

    override fun updateUser(new: UserInfo) {
        userHandler.update(new)
    }

    override fun updateState(newState: ClientSessionState) {
        state.value = newState
    }

    override fun clear() {
        state.value = ClientSessionState.None
        userHandler.update(null)
        session = null
    }
}

suspend fun signUpOrInFromPrivateKey(
    privateKey: String,
    sessionManager: SessionManager,
    isSignUp: Boolean
): Pair<RawUserPassInfo, UserInfo> {
    val sessionModel = sessionManager.sessionModel
    val publicKey = getDerPublicKeyFromPrivateKey(privateKey).getOrThrow()
    val data = sessionManager.getData().getOrThrow()
    val f = finalData(data)
    val sig = signature(privateKey, f).getOrThrow()
    val ad = calcAddress(publicKey).getOrThrow()
    val u = when {
        isSignUp -> sessionManager.signUp(publicKey, sig)
        else -> sessionManager.signIn(ad, sig)
    }.getOrThrow()
    val session = RawUserPassInfo(privateKey, publicKey, ad)
    sessionModel.updateUser(u)
    sessionModel.updateSignature(data, sig)
    return session to u
}
