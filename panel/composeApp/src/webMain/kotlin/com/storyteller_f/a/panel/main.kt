package com.storyteller_f.a.panel

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val panelUiViewModel = createPanelUIViewModel()
    ComposeViewport {
        App(panelUiViewModel)
    }
}