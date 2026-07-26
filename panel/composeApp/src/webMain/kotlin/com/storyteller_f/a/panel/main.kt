package com.storyteller_f.a.panel

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        val httpUrl = wasmQueryParameter("appiumHttpUrl") ?: PanelConfig.SERVER_URL
        val panelUiViewModel = remember(httpUrl) { PanelUIViewModel(MainScope(), httpUrl) }
        LaunchedEffect(panelUiViewModel) {
            if (window.location.search.contains("appium=true")) {
                panelUiViewModel.instance.collectLatest { instance ->
                    localStorage.setItem(
                        "appium.session_ready",
                        (instance is IPanelAccountInstance.Regular).toString(),
                    )
                }
            }
        }
        CompositionLocalProvider(LocalPanelUiViewModel provides panelUiViewModel) {
            App()
        }
    }
}

private fun wasmQueryParameter(name: String): String? = window.location.search
    .removePrefix("?")
    .split('&')
    .firstOrNull { it.substringBefore('=') == name }
    ?.substringAfter('=', missingDelimiterValue = "")
    ?.takeIf(String::isNotEmpty)
