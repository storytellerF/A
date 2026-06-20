package com.storyteller_f.a.panel

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.storyteller_f.shared.loadCryptoLibIfNeed
import com.storyteller_f.shared.setupKmpLogger
import io.github.aakira.napier.Napier
import java.awt.BorderLayout
import java.awt.Button
import java.awt.Dialog
import java.awt.Frame
import java.awt.TextArea
import kotlin.system.exitProcess

fun main() {
    setupKmpLogger()
    initForJvmMain()
    loadCryptoLibIfNeed()
    val panelUiViewModel = createPanelUIViewModel()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Panel",
        ) {
            App(panelUiViewModel)
        }
    }
}

private fun initForJvmMain() {
    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        Napier.e(e) {
            "uncaught exception"
        }
        Dialog(Frame(), e.message ?: "Error").apply {
            layout = BorderLayout()
            val label = TextArea(e.stackTraceToString())
            add(label, BorderLayout.CENTER)
            val button = Button("OK").apply {
                addActionListener {
                    dispose()
                    exitProcess(1)
                }
            }
            add(button, BorderLayout.SOUTH)
            setSize(300, 300)
            isVisible = true
        }
    }
}
