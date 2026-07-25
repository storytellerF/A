import com.storyteller_f.a.client.core.SimplePassHolder
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.buildWebSocketUrl
import com.storyteller_f.a.client.core.createSimpleUserSessionManager
import com.storyteller_f.a.client.core.defaultClientConfigure
import com.storyteller_f.a.client.core.getClient
import com.storyteller_f.a.client.core.userSignUp
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import org.junit.Rule
import org.junit.rules.TestName

abstract class DesktopAppiumTestBase {

    @get:Rule
    val name = TestName()
}

fun runAppiumBlockingTest(block: suspend () -> Unit) =
    runDesktopAppiumBlockingTest(block)

fun writeSessionFile(path: String, sessionJson: String) {
    writeDesktopSessionFile(path, sessionJson)
}

suspend fun createPreRegisteredSession(ports: AppiumPorts): InjectedSession {
    return createPreRegisteredInjectedSession { authKey, passHolder ->
        val manager = createDesktopApiSessionManager(ports, passHolder)
        try {
            manager.userSignUp(authKey, passHolder)
        } finally {
            manager.client.close()
        }
    }
}

suspend fun createAuthenticatedSession(ports: AppiumPorts): AuthenticatedSession {
    val session = createUnsignedInjectedSession()
    val passHolder = SimplePassHolder()
    val manager = createDesktopApiSessionManager(ports, passHolder)
    manager.userSignUp(session.toAuthKey(), passHolder)
    return AuthenticatedSession(session, manager)
}

private fun createDesktopApiSessionManager(
    ports: AppiumPorts,
    passHolder: SimplePassHolder
): UserSessionManager =
    createSimpleUserSessionManager(
        buildWebSocketUrl("ws://127.0.0.1:${ports.ws}"),
        AcceptAllCookiesStorage(),
        passHolder,
        { model, cookieStorage ->
            getClient {
                defaultClientConfigure(
                    cookieStorage,
                    model,
                    passHolder,
                    "http://127.0.0.1:${ports.server}"
                )
            }
        }
    ) { _, _, _ -> }
