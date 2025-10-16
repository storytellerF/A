package com.storyteller_f.a.app.compose_app.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.platform.Clipboard
import com.storyteller_f.a.app.compose_app.UIViewModel
import com.storyteller_f.shared.type.PrimaryKey
import com.strabled.composepreferences.utilis.DataStoreManager
import dev.jordond.connectivity.Connectivity
import io.ktor.http.ContentType
import kotlinx.io.Source

@Stable
interface ClientFile {
    val name: String
    val contentType: ContentType
    val size: Long
    val path: String

    fun source(): Source
}

class AppPlatform(val hasNativeBack: Boolean, val isActive: Boolean = true, val debug: Boolean)

expect val appPlatform: AppPlatform

expect fun initEnvironment(context: Any)

expect suspend fun Clipboard.setText(string: String)

expect fun getClientFile(path: String): ClientFile?

expect fun startCall(roomId: PrimaryKey)

expect fun createConnectivity(): Connectivity

expect fun getUiViewModel(): UIViewModel

@Composable
expect fun createCustomDataStoreManager(): DataStoreManager

expect fun unregisterPushService()
