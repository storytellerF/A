package com.storyteller_f.a.panel

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val panelUiViewModel = createPanelUIViewModel()
    ComposeViewport {
        CompositionLocalProvider(LocalPanelUiViewModel provides panelUiViewModel) {
            App()
        }
    }
}