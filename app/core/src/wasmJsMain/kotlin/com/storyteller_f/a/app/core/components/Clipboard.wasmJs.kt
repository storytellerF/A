package com.storyteller_f.a.app.core.components

import androidx.compose.ui.platform.Clipboard

actual suspend fun Clipboard.setText(string: String) = Unit
