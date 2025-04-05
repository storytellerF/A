package com.storyteller_f.a.app.utils

import androidx.compose.ui.platform.Clipboard

class Platform(val hasNativeBack: Boolean, val isActive: Boolean = true)

expect val platform: Platform

expect fun initEnvironment(context: Any)

expect suspend fun Clipboard.setText(string: String)
