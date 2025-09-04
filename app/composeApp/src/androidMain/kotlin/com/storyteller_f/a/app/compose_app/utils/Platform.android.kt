package com.storyteller_f.a.app.compose_app.utils

import android.content.ClipData
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import com.storyteller_f.a.app.BuildConfig
import com.storyteller_f.a.app.compose_app.AApplication
import com.storyteller_f.a.app.compose_app.UIViewModel
import com.storyteller_f.a.app.compose_app.compontents.mainAppRef
import com.storyteller_f.a.app.compose_app.getClipFile
import com.storyteller_f.a.app.compose_app.initFromContext
import com.storyteller_f.a.app.compose_app.pages.ClientFile
import com.storyteller_f.shared.appContextRef
import dev.jordond.connectivity.Connectivity

actual val platform: Platform
    get() {
        val activity = mainAppRef?.get()
        val currentState = activity?.lifecycle?.currentState
        val isActive = currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
        return Platform(true, isActive, BuildConfig.DEBUG)
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
    return (appContextRef.get() as AApplication).uiViewModel
}

actual fun getClientFile(path: String): ClientFile? {
    return getClipFile(appContextRef.get()!!, path.toUri())
}
