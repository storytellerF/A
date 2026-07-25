package com.storyteller_f.a.client.compose_core.components

import androidx.compose.ui.platform.Clipboard

expect suspend fun Clipboard.setText(string: String)
