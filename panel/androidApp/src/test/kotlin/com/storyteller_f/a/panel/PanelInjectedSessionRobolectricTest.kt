package com.storyteller_f.a.panel

import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import com.storyteller_f.a.app.core.utils.restoreFromStorage
import com.storyteller_f.a.client.core.ClientSessionState
import com.storyteller_f.a.dev.appium.PRIVATE_STORAGE_INJECTED_SESSION_DIR
import com.storyteller_f.a.dev.appium.buildInjectedSessionJson
import com.storyteller_f.a.dev.appium.createUnsignedInjectedSession
import com.storyteller_f.a.dev.appium.writeInjectedSessionFile
import com.storyteller_f.shared.loadCryptoLibIfNeed
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertIs

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = PanelApplication::class, sdk = [35])
class PanelInjectedSessionRobolectricTest {
    private val application: PanelApplication
        get() = org.robolectric.RuntimeEnvironment.getApplication() as PanelApplication

    @org.junit.Before
    fun setup() {
        application.filesDir.resolve(PRIVATE_STORAGE_INJECTED_SESSION_DIR).deleteRecursively()
        application.getSharedPreferences(TEST_SETTINGS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @org.junit.Test
    fun restoresInjectedPrivateSessionWithoutAppium() = runTest {
        loadCryptoLibIfNeed()
        val injectedSession = createUnsignedInjectedSession()
        writeInjectedSessionFile(application.filesDir, buildInjectedSessionJson(injectedSession))

        val settings = SharedPreferencesSettings(
            application.getSharedPreferences(TEST_SETTINGS_NAME, Context.MODE_PRIVATE)
        )

        val clientSessionState = restoreFromStorage(settings)

        val state = assertIs<ClientSessionState.Success>(clientSessionState)
        assertEquals(injectedSession.address, state.userPass.address().getOrThrow())
    }

    private companion object {
        const val TEST_SETTINGS_NAME = "panel-robolectric-injected-session-test"
    }
}
