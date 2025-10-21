package com.storyteller_f.a.cloud.panel

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Panel",
    ) {
        App()
    }
}