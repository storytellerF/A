package com.storyteller_f.a.app.compose_app.utils

import androidx.compose.ui.platform.Clipboard
import com.storyteller_f.a.app.compose_app.UIViewModel
import com.storyteller_f.a.app.compose_app.pages.ClientFile
import com.storyteller_f.shared.type.PrimaryKey
import dev.jordond.connectivity.Connectivity

class AppPlatform(val hasNativeBack: Boolean, val isActive: Boolean = true, val debug: Boolean)

expect val appPlatform: AppPlatform

expect fun initEnvironment(context: Any)

expect suspend fun Clipboard.setText(string: String)

expect fun createConnectivity(): Connectivity

expect fun getUiViewModel(): UIViewModel

expect fun getClientFile(path: String): ClientFile?

expect fun startCall(roomId: PrimaryKey)
