/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium

import com.storyteller_f.a.client.core.AuthKey
import com.storyteller_f.a.client.core.PanelSessionManager
import com.storyteller_f.a.client.core.SimplePassHolder
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.buildWebSocketUrl
import com.storyteller_f.a.client.core.createSimplePanelSessionManager
import com.storyteller_f.a.client.core.createSimpleUserSessionManager
import com.storyteller_f.a.client.core.defaultClientConfigure
import com.storyteller_f.a.client.core.defaultClientConfigureForPanel
import com.storyteller_f.a.client.core.getClient
import com.storyteller_f.a.client.core.panelSignUp
import com.storyteller_f.a.client.core.userSignUp
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.loadCryptoLibIfNeed
import com.storyteller_f.shared.model.AlgoType
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

data class DesktopAppiumRuntimeConfig(
    val suiteName: String,
    val appLabel: String,
    val mainClassName: String,
    val runtimeClasspathCandidates: List<File>,
    val runtimeClasspathErrorMessage: String,
    val scriptPrefix: String,
    val includeWsUrl: Boolean,
    /** Maximum time to wait for the desktop application's main window. */
    val windowWaitSeconds: Long = 120L,
)

abstract class TargetAppiumHelper {
    abstract val androidApp: AppUnderTest
    abstract val desktopRuntimeConfig: DesktopAppiumRuntimeConfig
    abstract val suiteName: String

    protected suspend fun createPreRegisteredInjectedSession(
        signUp: suspend (AuthKey, SimplePassHolder) -> Unit,
    ): InjectedSession {
        val session = createUnsignedInjectedSession()
        val passHolder = SimplePassHolder()
        signUp(session.toAuthKey(), passHolder)
        return session
    }

    protected suspend fun createUnsignedInjectedSession(): InjectedSession {
        val algo = getAlgo(AlgoType.P256)
        val (pemPrivateKey, _) = algo.generatePemKeyPair().getOrThrow()
        val derPrivateKey = algo.getDerPrivateKey(pemPrivateKey).getOrThrow()
        val derPublicKey = algo.getDerPublicKeyFromPrivateKey(pemPrivateKey).getOrThrow()
        return InjectedSession(
            address = algo.calcAddress(derPublicKey).getOrThrow(),
            pemPrivateKey = pemPrivateKey,
            derPrivateKey = derPrivateKey,
            derPublicKey = derPublicKey,
        )
    }

    protected fun resolvePackageName(metadataCandidates: Sequence<File>): String {
        val metadataFile =
            metadataCandidates.firstOrNull { it.exists() }
                ?: error("Android APK metadata file not found")
        val applicationId =
            Json.parseToJsonElement(metadataFile.readText())
                .jsonObject["applicationId"]
                ?.jsonPrimitive
                ?.contentOrNull
        check(!applicationId.isNullOrBlank()) { "Unable to read applicationId from ${metadataFile.path}" }
        return applicationId
    }
}

class AppAppiumHelper : TargetAppiumHelper() {
    override val androidApp: AppUnderTest by lazy {
        AppUnderTest(
            packageName =
            resolvePackageName(
                sequenceOf(
                    File("../../app/androidApp/build/outputs/apk/debug/output-metadata.json"),
                    File("app/androidApp/build/outputs/apk/debug/output-metadata.json"),
                ),
            ),
            mainActivityClassName = "com.storyteller_f.a.app.MainActivity",
        )
    }
    override val desktopRuntimeConfig =
        DesktopAppiumRuntimeConfig(
            suiteName = "DesktopAppiumTest",
            appLabel = "Desktop app",
            mainClassName = "com.storyteller_f.a.app.JvmMainKt",
            runtimeClasspathCandidates =
            listOf(
                File("../../app/desktopApp/build/appium/runtimeClasspath.txt"),
                File("app/desktopApp/build/appium/runtimeClasspath.txt"),
            ),
            runtimeClasspathErrorMessage =
            "Desktop runtime classpath not found. " +
                "Run :app:desktopApp:writeAppiumRuntimeClasspath first.",
            scriptPrefix = "desktop-appium-",
            includeWsUrl = true,
        )
    override val suiteName = "AppAppiumTest"

    fun readSystemPrivateKey(): String =
        File(resolveAppiumPresetPath(), "secrets/p-system").readText().replace("\r\n", "\n")

    suspend fun createPreRegisteredSession(ports: AppiumPorts): InjectedSession {
        loadCryptoLibIfNeed()
        return createPreRegisteredInjectedSession { authKey, passHolder ->
            val manager = createApiSessionManager(ports, passHolder)
            try {
                manager.userSignUp(authKey, passHolder)
            } finally {
                manager.client.close()
            }
        }
    }

    suspend fun createAuthenticatedSession(ports: AppiumPorts): AuthenticatedSession {
        loadCryptoLibIfNeed()
        val session = createUnsignedInjectedSession()
        val passHolder = SimplePassHolder()
        val manager = createApiSessionManager(ports, passHolder)
        manager.userSignUp(session.toAuthKey(), passHolder)
        return AuthenticatedSession(session, manager)
    }

    private fun createApiSessionManager(ports: AppiumPorts, passHolder: SimplePassHolder): UserSessionManager =
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
                        "http://127.0.0.1:${ports.server}",
                    )
                }
            },
        ) { _, _, _ -> }
}

class PanelAppiumHelper : TargetAppiumHelper() {
    override val androidApp: AppUnderTest by lazy {
        AppUnderTest(
            packageName =
            resolvePackageName(
                sequenceOf(
                    File("../../panel/androidApp/build/outputs/apk/debug/output-metadata.json"),
                    File("panel/androidApp/build/outputs/apk/debug/output-metadata.json"),
                ),
            ),
            mainActivityClassName = "com.storyteller_f.a.panel.MainActivity",
        )
    }
    override val desktopRuntimeConfig =
        DesktopAppiumRuntimeConfig(
            suiteName = "DesktopPanelAppiumTest",
            appLabel = "Desktop panel app",
            mainClassName = "com.storyteller_f.a.panel.PanelMainKt",
            runtimeClasspathCandidates =
            listOf(
                File("../../panel/desktopApp/build/appium/runtimeClasspath.txt"),
                File("panel/desktopApp/build/appium/runtimeClasspath.txt"),
            ),
            runtimeClasspathErrorMessage =
            "Panel desktop runtime classpath not found. " +
                "Run :panel:desktopApp:writeAppiumRuntimeClasspath first.",
            scriptPrefix = "desktop-panel-appium-",
            includeWsUrl = false,
        )
    override val suiteName = "PanelAppiumTest"

    suspend fun createPreRegisteredSession(ports: AppiumPorts): InjectedSession {
        loadCryptoLibIfNeed()
        return createPreRegisteredInjectedSession { authKey, passHolder ->
            val manager = createApiSessionManager(ports, passHolder)
            try {
                manager.panelSignUp(authKey, passHolder)
            } finally {
                manager.client.close()
            }
        }
    }

    private fun createApiSessionManager(ports: AppiumPorts, passHolder: SimplePassHolder): PanelSessionManager =
        createSimplePanelSessionManager(
            passHolder,
            AcceptAllCookiesStorage(),
        ) { model, cookieStorage ->
            getClient {
                defaultClientConfigureForPanel(
                    cookieStorage,
                    model,
                    passHolder,
                    "http://127.0.0.1:${ports.server}",
                )
            }
        }
}
