package com.storyteller_f.a.app.utils

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.Lifecycle
import com.storyteller_f.a.app.AppConfig
import com.storyteller_f.a.app.core.components.mainActivityRef
import com.storyteller_f.a.app.getClipFile
import com.storyteller_f.a.app.initFromContext
import com.storyteller_f.shared.getAppContextRefValue
import com.strabled.composepreferences.utilis.DataStoreManager
import dev.jordond.connectivity.Connectivity
import okio.Path.Companion.toOkioPath
import org.unifiedpush.android.connector.UnifiedPush

actual val appPlatform: AppPlatform
    get() {
        val activity = mainActivityRef?.get()
        val currentState = activity?.lifecycle?.currentState
        val isActive = currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
        return AppPlatform(true, isActive, AppConfig.DEBUG)
    }

actual fun initEnvironment(context: Any) {
    if (context is ComponentActivity) {
        context.initFromContext()
    }
}

actual fun createConnectivity(): Connectivity {
    return Connectivity {
        autoStart = true
    }
}

actual fun getClientFile(path: String): ClientFile? {
    return getClipFile(getAppContextRefValue()!!, path.toUri())
}

private val store by lazy {
    val context = getAppContextRefValue()!!
    DataStoreManager(
        PreferenceDataStoreFactory.createWithPath(
            produceFile = {
                context.filesDir.resolve("main.preferences_pb").toOkioPath()
            }
        )
    )
}

@Composable
actual fun createCustomDataStoreManager(): DataStoreManager {
    return store
}

actual fun unregisterPushService() {
    val context = getAppContextRefValue() ?: return
    UnifiedPush.unregister(context, "A")
}

actual fun getDeepLinkHost(): String {
    return AppConfig.DEEP_LINK_HOST
}

actual fun getDeepLinkScheme(): String {
    return "${AppConfig.DEEP_LINK_SCHEME_PREFIX}${if (AppConfig.DEBUG) "-debug" else ""}"
}
