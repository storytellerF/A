package com.storyteller_f.a.app.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap
import com.storyteller_f.shared.model.RoomInfo
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

expect fun getClientFile(path: String): ClientFile?

expect fun startCall(roomId: PrimaryKey)

expect fun createConnectivity(): Connectivity

@Composable
expect fun createCustomDataStoreManager(): DataStoreManager

expect fun unregisterPushService()

expect suspend fun notifyNotification(room: RoomInfo, bitmap: ImageBitmap?)

expect fun getDeepLinkHost(): String

expect fun getDeepLinkScheme(): String
