import com.storyteller_f.a.client.core.SimplePassHolder
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.buildWebSocketUrl
import com.storyteller_f.a.client.core.createSimpleUserSessionManager
import com.storyteller_f.a.client.core.defaultClientConfigure
import com.storyteller_f.a.client.core.getClient
import com.storyteller_f.a.client.core.userSignUp
import com.storyteller_f.a.dev.appium.InjectedSession
import com.storyteller_f.a.dev.appium.createUnsignedInjectedSession
import com.storyteller_f.a.dev.appium.toAuthKey
import io.appium.java_client.AppiumDriver
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import org.junit.Rule
import org.junit.rules.TestName

abstract class DesktopAppiumTestBase {

    @get:Rule
    val name = TestName()

    protected fun runAppiumBlockingTest(block: suspend () -> Unit) =
        runDesktopAppiumBlockingTest(block)

    protected suspend fun runDesktopAppiumTest(block: suspend (AppiumDriver) -> Unit) {
        runDesktopAppiumTestWithSetup(beforeLaunch = { _, _ -> }) { driver, _ -> block(driver) }
    }

    protected suspend fun <T> runDesktopAppiumTestWithSetup(
        beforeLaunch: suspend (ports: AppiumPorts, sessionFilePath: String) -> T,
        block: suspend (AppiumDriver, T) -> Unit,
    ) = runConfiguredDesktopAppiumTestWithSetup(
        testName = name.methodName,
        config = appDesktopRuntimeConfig,
        beforeLaunch = beforeLaunch,
        block = block,
    )

    protected fun writeSessionFile(path: String, sessionJson: String) {
        writeDesktopSessionFile(path, sessionJson)
    }

    protected suspend fun createPreRegisteredSession(ports: AppiumPorts): InjectedSession {
        return createPreRegisteredInjectedSession { authKey, passHolder ->
            val manager = createDesktopApiSessionManager(ports, passHolder)
            try {
                manager.userSignUp(authKey, passHolder)
            } finally {
                manager.client.close()
            }
        }
    }

    protected suspend fun createAuthenticatedSession(ports: AppiumPorts): AuthenticatedSession {
        val session = createUnsignedInjectedSession()
        val passHolder = SimplePassHolder()
        val manager = createDesktopApiSessionManager(ports, passHolder)
        manager.userSignUp(session.toAuthKey(), passHolder)
        return AuthenticatedSession(session, manager)
    }
}

private fun createDesktopApiSessionManager(ports: AppiumPorts, passHolder: SimplePassHolder): UserSessionManager =
    createSimpleUserSessionManager(
        buildWebSocketUrl("ws://127.0.0.1:${ports.ws}"),
        AcceptAllCookiesStorage(),
        passHolder,
        { model, cookieStorage ->
            getClient {
                defaultClientConfigure(cookieStorage, model, passHolder, "http://127.0.0.1:${ports.server}")
            }
        }
    ) { _, _, _ -> }
