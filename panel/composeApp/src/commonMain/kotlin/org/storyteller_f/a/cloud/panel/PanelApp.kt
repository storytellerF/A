package org.storyteller_f.a.cloud.panel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.russhwolf.settings.Settings
import com.storyteller_f.a.app.core.utils.createSettings
import com.storyteller_f.a.app.core.utils.restoreFromStorage
import com.storyteller_f.a.client.core.PanelSessionManager
import com.storyteller_f.a.client.core.PanelSessionModel
import com.storyteller_f.a.client.core.createPanelSessionManager
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.CookiesStorage
import org.jetbrains.compose.resources.stringResource

@Composable
fun App() {
    MaterialTheme {
        val navigator = rememberNavController()
        NavHost(navigator, "login") {
            composable("login") {

            }
        }
    }
}

@Composable
fun PanelLoginPage() {
    Surface {

    }
}

class CustomPanelSessionManager(
    val proxy: PanelSessionManager,
    val settings: Settings,
) : PanelSessionManager by proxy

fun createCustomPanelSessionManager(
    settingsName: String,
    createClient: (PanelSessionModel, CookiesStorage) -> HttpClient,
): CustomPanelSessionManager {
    val settings = createSettings(settingsName)
    val customSessionManager = createPanelSessionManager(createClient)
    customSessionManager.restoreFromStorage(settings)
    return CustomPanelSessionManager(customSessionManager, settings)
}
