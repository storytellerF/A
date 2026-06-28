package com.storyteller_f.a.app.utils

import androidx.compose.runtime.Composable
import com.storyteller_f.a.app.AppConfig
import com.strabled.composepreferences.utilis.DataStoreManager
import dev.jordond.connectivity.Connectivity
import dev.jordond.connectivity.ConnectivityProvider
import kotlinx.coroutines.flow.flowOf

actual val appPlatform: AppPlatform
    get() = AppPlatform(hasNativeBack = false, debug = false)

actual fun initEnvironment(context: Any) = Unit

actual fun createConnectivity(): Connectivity {
    return Connectivity(ConnectivityProvider(flowOf(Connectivity.Status.Connected(metered = false))))
}

actual fun getClientFile(path: String): ClientFile? = null

@Composable
actual fun createCustomDataStoreManager(): DataStoreManager {
    return DataStoreManager()
}

actual fun unregisterPushService() = Unit

actual fun getDeepLinkHost(): String = AppConfig.DEEP_LINK_HOST

actual fun getDeepLinkScheme(): String = AppConfig.DEEP_LINK_SCHEME_PREFIX
