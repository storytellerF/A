import com.storyteller_f.a.client.core.AuthKey
import com.storyteller_f.a.client.core.SimplePassHolder
import com.storyteller_f.a.client.core.panelSignUp
import com.storyteller_f.a.dev.appium.InjectedSession
import com.storyteller_f.a.dev.appium.createUnsignedInjectedSession
import com.storyteller_f.a.dev.appium.toAuthKey
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.model.AlgoType

suspend fun createPreRegisteredInjectedSession(
    signUp: suspend (AuthKey, SimplePassHolder) -> Unit,
): InjectedSession {
    val session = createUnsignedInjectedSession()
    val passHolder = SimplePassHolder()
    signUp(session.toAuthKey(), passHolder)
    return session
}

suspend fun createPreRegisteredPanelSession(ports: AppiumPorts): InjectedSession =
    createPreRegisteredInjectedSession { authKey, passHolder ->
        val manager = createPanelApiSessionManager(ports, passHolder)
        try {
            manager.panelSignUp(authKey, passHolder)
        } finally {
            manager.client.close()
        }
    }

suspend fun generateAppiumPrivateKey(): String =
    getAlgo(AlgoType.P256).generatePemKeyPair().getOrThrow().first
