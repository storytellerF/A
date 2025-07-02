package com.storyteller_f.a.app.compose_app.utils

import androidx.compose.ui.platform.Clipboard
import dev.jordond.connectivity.Connectivity

class Platform(val hasNativeBack: Boolean, val isActive: Boolean = true, val debug: Boolean)

expect val platform: Platform

expect fun initEnvironment(context: Any)

expect suspend fun Clipboard.setText(string: String)

expect fun createConnectivity(): Connectivity
