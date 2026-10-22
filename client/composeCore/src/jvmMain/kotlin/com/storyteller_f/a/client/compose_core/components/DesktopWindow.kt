/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.client.compose_core.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.window.TitleBar
import dev.nucleusframework.window.styling.LocalTitleBarStyle

@Composable
fun DesktopWindow(title: String, onCloseRequest: () -> Unit, content: @Composable () -> Unit) {
    val navigationInput = remember { DesktopBackNavigationInput() }
    DecoratedWindow(title = title, onCloseRequest = onCloseRequest, onKeyEvent = navigationInput::onKeyEvent) {
        val dispatcher = requireNotNull(LocalNavigationEventDispatcherOwner.current).navigationEventDispatcher
        DisposableEffect(dispatcher, navigationInput) {
            dispatcher.addInput(navigationInput)
            onDispose { dispatcher.removeInput(navigationInput) }
        }
        TitleBar { BasicText(title, style = TextStyle(color = LocalTitleBarStyle.current.colors.content)) }
        content()
    }
}

/** Supplies the same Escape-to-navigation binding as Compose's AWT window mediator. */
internal class DesktopBackNavigationInput : NavigationEventInput() {
    private var attached = false

    override fun onAdded(dispatcher: NavigationEventDispatcher) {
        attached = true
    }

    override fun onRemoved() {
        attached = false
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (!attached || event.type != KeyEventType.KeyDown || event.key != Key.Escape) return false
        dispatchOnBackCompleted()
        return true
    }
}
