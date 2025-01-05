package com.storyteller_f.a.app.compontents

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import io.github.aakira.napier.Napier
import java.lang.ref.WeakReference

val requestQueue = mutableStateListOf<Permission>()

@Composable
actual fun isPermissionGranted(permission: Permission): MutableState<Boolean> {
    val isGranted = remember {
        mutableStateOf(false)
    }
    val context = LocalContext.current
    LaunchedEffect(context, requestQueue) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        isGranted.value = granted
    }
    return isGranted
}

actual fun requestPermission(permission: Permission) {
    val launcher = launcherRef?.get()
    if (launcher == null) {
        Napier.i {
            "request permission failed, because of launcher is null $launcherRef"
        }
        return
    }
    val p = when (permission) {
        is Permission.Audio -> Manifest.permission.RECORD_AUDIO
        else -> return
    }
    requestQueue.add(permission)
    launcher.launch(p)
}

var launcherRef: WeakReference<ActivityResultLauncher<String>>? = null

fun bindActivity(activity: ComponentActivity) {
    val launcher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            requestQueue.removeAt(0)
        }
    }
    launcherRef = WeakReference(launcher)
}

fun unbindActivity() {
    launcherRef?.get()?.unregister()
    launcherRef = null
}
