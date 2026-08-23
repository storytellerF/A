/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.app.utils

import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.Lifecycle
import com.storyteller_f.a.app.AppConfig
import com.storyteller_f.a.app.getClipFile
import com.storyteller_f.a.app.initFromContext
import com.storyteller_f.a.client.compose_core.components.mainActivityRef
import com.storyteller_f.shared.getAppContextRefValue
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

actual fun createConnectivity(): Connectivity =
    Connectivity {
    autoStart = true
}

actual fun getClientFile(path: String): ClientFile? =
    getClipFile(checkNotNull(getAppContextRefValue()) { "Application context is not initialized" }, path.toUri())

private val appPreferencesDataStore by lazy {
    val context = checkNotNull(getAppContextRefValue()) { "Application context is not initialized" }
    PreferenceDataStoreFactory.createWithPath(
        produceFile = {
            context.filesDir.resolve("main.preferences_pb").toOkioPath()
        },
    )
}

internal actual fun createAppPreferencesDataStore(): DataStore<Preferences> = appPreferencesDataStore

actual fun unregisterPushService() {
    val context = getAppContextRefValue() ?: return
    UnifiedPush.unregister(context, "A")
}

actual fun getDeepLinkHost(): String = AppConfig.DEEP_LINK_HOST

actual fun getDeepLinkScheme(): String = "${AppConfig.DEEP_LINK_SCHEME_PREFIX}${if (AppConfig.DEBUG) "-debug" else ""}"
