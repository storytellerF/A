package com.storyteller_f.a.panel

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController {
    val panelUiViewModel = remember { createPanelUIViewModel() }
    CompositionLocalProvider(LocalPanelUiViewModel provides panelUiViewModel) {
        App()
    }
}
