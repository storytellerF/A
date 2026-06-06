package com.storyteller_f.a.panel

import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import com.storyteller_f.a.app.core.utils.restoreFromStorage
import com.storyteller_f.a.client.core.ClientSessionState
import com.storyteller_f.a.client.core.createPanelSessionManager
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.loadCryptoLibIfNeed
import com.storyteller_f.shared.model.AlgoType
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import java.io.BufferedOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertIs

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = PanelApplication::class, sdk = [35])
class PanelInjectedSessionRobolectricTest {
    private val application: PanelApplication
        get() = org.robolectric.RuntimeEnvironment.getApplication() as PanelApplication

    @org.junit.Before
    fun setup() {
        application.filesDir.resolve(INJECTED_SESSION_DIR).deleteRecursively()
        application.getSharedPreferences(TEST_SETTINGS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @org.junit.Test
    fun restoresInjectedPrivateSessionWithoutAppium() = runTest {
        loadCryptoLibIfNeed()
        val injectedSession = createInjectedSessionJson()
        writeInjectedSession(injectedSession.json)

        val sessionManager = createPanelSessionManager { _, _ -> HttpClient {} }
        val settings = SharedPreferencesSettings(
            application.getSharedPreferences(TEST_SETTINGS_NAME, Context.MODE_PRIVATE)
        )

        sessionManager.restoreFromStorage(settings)

        val state = assertIs<ClientSessionState.Success>(sessionManager.model.state.value)
        assertEquals(injectedSession.address, state.userPass.address().getOrThrow())
    }

    private suspend fun createInjectedSessionJson(): InjectedSessionJson {
        val algo = getAlgo(AlgoType.P256)
        val pemPrivateKey = algo.generatePemKeyPair().getOrThrow().first
        val derPrivateKey = algo.getDerPrivateKey(pemPrivateKey).getOrThrow()
        val derPublicKey = algo.getDerPublicKeyFromPrivateKey(pemPrivateKey).getOrThrow()
        val address = algo.calcAddress(derPublicKey).getOrThrow()
        val json = """
            {
              "algo": "P256",
              "address": "$address",
              "pemPrivateKey": ${pemPrivateKey.jsonString()},
              "derPrivateKey": "$derPrivateKey",
              "derPublicKey": "$derPublicKey"
            }
        """.trimIndent()
        return InjectedSessionJson(address, json)
    }

    private fun writeInjectedSession(json: String) {
        val sessionFile = application.filesDir
            .resolve(INJECTED_SESSION_DIR)
            .resolve(INJECTED_SESSION_FILE)
        sessionFile.parentFile?.mkdirs()
        BufferedOutputStream(sessionFile.outputStream()).use { output ->
            output.write(json.encodeToByteArray())
        }
    }

    private fun String.jsonString(): String {
        val escaped = buildString {
            this@jsonString.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
        return "\"$escaped\""
    }

    private data class InjectedSessionJson(
        val address: String,
        val json: String,
    )

    private companion object {
        const val INJECTED_SESSION_DIR = "appium-session"
        const val INJECTED_SESSION_FILE = "session.json"
        const val TEST_SETTINGS_NAME = "panel-robolectric-injected-session-test"
    }
}
