package com.storyteller_f.a.client.core

import com.storyteller_f.a.api.SignInBody
import com.storyteller_f.a.api.SignUpBody
import com.storyteller_f.shared.finalData
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.model.PanelAccountInfo
import com.storyteller_f.shared.model.PrimaryKeyIdentifiable
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.checkTsIsValid
import com.storyteller_f.shared.utils.extractMarkdownMediaLink
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.ExperimentalTime

interface SessionModel<U> {
    val uid: PrimaryKey?
    val dataAndSignature: Pair<String, String?>?
    val currentUserPass: UserPass?
    val state: StateFlow<ClientSessionState>
    val userHandler: FixedLoadingHandler<U?>
    fun updateSignature(data: String, signature: String?)
    fun generateData(): String
    fun clear()
    fun updateState(newState: ClientSessionState)
    fun updateUser(u: U)
}

class SimpleSessionModel<U : PrimaryKeyIdentifiable> : SessionModel<U> {
    override val state = MutableStateFlow<ClientSessionState>(ClientSessionState.None)

    // 用于header 和server 协商被签名的数据
    private var currentStamp = 0L
    override val uid: PrimaryKey?
        get() = userHandler.data.value?.id

    // currentData 是本地使用的，但是还是需要依据server 的为准
    override var dataAndSignature: Pair<String, String?>? = null
    override val currentUserPass: UserPass?
        get() = (state.value as? ClientSessionState.Success)?.userPass
    override val userHandler = FixedLoadingHandler<U?>()

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

    override fun updateUser(u: U) {
        userHandler.done(u)
    }

    override fun updateState(newState: ClientSessionState) {
        state.value = newState
    }

    override fun clear() {
        state.value = ClientSessionState.None
        userHandler.done(null)
        dataAndSignature = null
    }
}

class UserSessionModel(val simpleSessionModel: SimpleSessionModel<UserInfo> = SimpleSessionModel()) :
    SessionModel<UserInfo> by simpleSessionModel

class PanelSessionModel(val simpleSessionModel: SimpleSessionModel<PanelAccountInfo> = SimpleSessionModel()) :
    SessionModel<PanelAccountInfo> by simpleSessionModel

interface SessionManager<U> {
    val client: HttpClient
    val model: SessionModel<U>
    val isAlreadySignIn: StateFlow<Boolean>
    val address: StateFlow<String?>

    val currentIsAlreadySignUp: Boolean get() = isAlreadySignIn.value

    suspend fun updateAddress(clientSessionState: ClientSessionState)
}

interface UserSessionManager : SessionManager<UserInfo> {
    val webSocketClient: WebSocketClientImpl
}

class SimpleUserSessionManager(
    override val client: HttpClient,
    override val webSocketClient: WebSocketClientImpl,
    override val model: SessionModel<UserInfo>,
) : UserSessionManager {
    override val isAlreadySignIn = MutableStateFlow(false)
    override val address = MutableStateFlow<String?>(null)
    override suspend fun updateAddress(clientSessionState: ClientSessionState) {
        address.value =
            (clientSessionState as? ClientSessionState.Success)?.userPass?.address()?.getOrNull()
        isAlreadySignIn.value = clientSessionState is ClientSessionState.Success
    }
}

interface PanelSessionManager : SessionManager<PanelAccountInfo>

class SimplePanelSessionManager(
    override val client: HttpClient,
    override val model: SessionModel<PanelAccountInfo>
) : PanelSessionManager {
    override val isAlreadySignIn = MutableStateFlow(false)
    override val address = MutableStateFlow<String?>(null)

    override suspend fun updateAddress(clientSessionState: ClientSessionState) {
        address.value =
            (clientSessionState as? ClientSessionState.Success)?.userPass?.address()?.getOrNull()
        isAlreadySignIn.value = clientSessionState is ClientSessionState.Success
    }
}

fun createUserSessionManager(
    webSocketUrl: String,
    createClient: (UserSessionModel, CookiesStorage) -> HttpClient,
    onReceiveFrame: suspend (RoomFrame, UserSessionModel, DefaultClientWebSocketSession) -> Unit,
): SimpleUserSessionManager {
    val cookieManager = AcceptAllCookiesStorage()
    val model = UserSessionModel()
    val client = createClient(model, cookieManager)
    val webSocketClient = WebSocketClientImpl(
        model,
        { userInfo, sig ->
            client.webSocketSession(webSocketUrl) {
                addRequestHeadersFromInfo(userInfo, sig)
            }
        },
    ) { frame, session ->
        onReceiveFrame(frame, model, session)
    }
    return SimpleUserSessionManager(client, webSocketClient, model)
}

fun createPanelSessionManager(
    createClient: (PanelSessionModel, CookiesStorage) -> HttpClient,
): SimplePanelSessionManager {
    val cookieManager = AcceptAllCookiesStorage()
    val model = PanelSessionModel()
    val client = createClient(model, cookieManager)
    return SimplePanelSessionManager(client, model)
}

suspend fun UserSessionManager.login() {
    val userPass = model.currentUserPass ?: return
    val userHandler = model.userHandler
    userHandler.request({
        userHandler.done(it)
    }) {
        runCatching {
            val data = getData().getOrThrow()
            val address = userPass.address().getOrThrow()
            val signature = userPass.signature(finalData(data)).getOrThrow()
            val userInfo = signIn(SignInBody(address, signature)).getOrThrow()
            model.updateSignature(data, signature)
            userInfo
        }
    }
}

suspend fun PanelSessionManager.login() {
    val userPass = model.currentUserPass ?: return
    val userHandler = model.userHandler
    userHandler.request({
        userHandler.done(it)
    }) {
        runCatching {
            val data = getData().getOrThrow()
            val address = userPass.address().getOrThrow()
            val signature = userPass.signature(finalData(data)).getOrThrow()
            val userInfo = signIn(SignInBody(address, signature)).getOrThrow()
            model.updateSignature(data, signature)
            userInfo
        }
    }
}

suspend fun UserSessionManager.getUserInfo(
    pemPrivateKey: String,
    isSignUp: Boolean,
    buildUserPass: suspend (RawUserPassInfo) -> UserPass
) = signUpOrInFromPrivateKey(pemPrivateKey, {
    getData()
}, buildUserPass) { publicKey, signature, address ->
    when {
        isSignUp -> signUp(SignUpBody(publicKey, signature))
        else -> signIn(SignInBody(address, signature))
    }
}

suspend fun PanelSessionManager.getPanelAccountInfo(
    pemPrivateKey: String,
    isSignUp: Boolean,
    buildUserPass: suspend (RawUserPassInfo) -> UserPass
) = signUpOrInFromPrivateKey(pemPrivateKey, {
    getData()
}, buildUserPass) { publicKey, signature, address ->
    when {
        isSignUp -> signUp(SignUpBody(publicKey, signature))
        else -> signIn(SignInBody(address, signature))
    }
}

suspend fun <U> SessionManager<U>.signUpOrInFromPrivateKey(
    pemPrivateKey: String,
    getData: suspend () -> Result<String>,
    buildUserPass: suspend (RawUserPassInfo) -> UserPass,
    getUserInfo: suspend (String, String, String) -> Result<U>
): U {
    getAlgo().run {
        val publicKey = getDerPublicKeyFromPrivateKey(pemPrivateKey).getOrThrow()
        val data = getData().getOrThrow()
        val f = finalData(data)
        val signature = signature(pemPrivateKey, f).getOrThrow()
        val address = calcAddress(publicKey).getOrThrow()
        val userInfo = getUserInfo(publicKey, signature, address).getOrThrow()
        model.updateUser(userInfo)
        model.updateSignature(data, signature)
        val userPassInfo = RawUserPassInfo(pemPrivateKey, publicKey, address)
        model.updateState(ClientSessionState.Success(buildUserPass(userPassInfo)))
        return userInfo
    }
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun processEncryptedTopic(
    topicInfos: List<TopicInfo>,
    manager: UserSessionManager
): List<TopicInfo> {
    val model = manager.model
    val uid = model.uid
    val key = model.currentUserPass
    return topicInfos.map { topicInfo ->
        val content = topicInfo.content
        if (content !is TopicContent.Encrypted) {
            topicInfo
        } else if (uid == null || key == null) {
            topicInfo.copy(content = TopicContent.Nil)
        } else {
            val s = content.encryptedKey[uid]
            if (s != null) {
                val topicContent = key.decrypt(
                    content.encrypted.hexToByteArray(),
                    s.hexToByteArray()
                ).fold(onSuccess = {
                    val mediaInfos = extractMarkdownMediaLink(it).mapNotNull { mediaName ->
                        manager.getMediaByName(mediaName, topicInfo.rootId, topicInfo.rootType)
                            .getOrNull()
                    }
                    TopicContent.Plain(it, mediaInfos)
                }, onFailure = {
                    TopicContent.DecryptFailed(it.message.toString())
                })
                topicInfo.copy(content = topicContent)
            } else {
                topicInfo.copy(content = TopicContent.DecryptFailed("auth failed"))
            }
        }
    }
}
