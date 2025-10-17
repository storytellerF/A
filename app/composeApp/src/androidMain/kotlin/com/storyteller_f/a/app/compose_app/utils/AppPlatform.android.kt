package com.storyteller_f.a.app.compose_app.utils

import android.content.ClipData
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.core.net.toUri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.Lifecycle
import com.storyteller_f.a.app.BuildConfig
import com.storyteller_f.a.app.compose_app.AApplication
import com.storyteller_f.a.app.compose_app.RTCActivity
import com.storyteller_f.a.app.compose_app.UIViewModel
import com.storyteller_f.a.app.compose_app.components.mainAppRef
import com.storyteller_f.a.app.compose_app.getClipFile
import com.storyteller_f.a.app.compose_app.initFromContext
import com.storyteller_f.a.app.compose_app.uiViewModel
import com.storyteller_f.shared.getAppContextRefValue
import com.storyteller_f.shared.type.PrimaryKey
import com.strabled.composepreferences.utilis.DataStoreManager
import dev.jordond.connectivity.Connectivity
import okio.Path.Companion.toOkioPath
import org.unifiedpush.android.connector.UnifiedPush

actual val appPlatform: AppPlatform
    get() {
        val activity = mainAppRef?.get()
        val currentState = activity?.lifecycle?.currentState
        val isActive = currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
        return AppPlatform(true, isActive, BuildConfig.DEBUG)
    }

actual fun initEnvironment(context: Any) {
    if (context is ComponentActivity) {
        context.initFromContext()
    }
}

actual suspend fun Clipboard.setText(string: String) {
    setClipEntry(ClipEntry(ClipData.newPlainText("text", string)))
}

actual fun createConnectivity(): Connectivity {
    return Connectivity {
        autoStart = true
    }
}

actual fun getUiViewModel(): UIViewModel {
    (getAppContextRefValue() as AApplication)
    return uiViewModel
}

actual fun getClientFile(path: String): ClientFile? {
    return getClipFile(getAppContextRefValue()!!, path.toUri())
}

actual fun startCall(roomId: PrimaryKey) {
    val application = getAppContextRefValue() ?: return
    val intent = Intent(application, RTCActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
    }
    intent.putExtra("roomId", roomId)
    application.startActivity(intent)
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
