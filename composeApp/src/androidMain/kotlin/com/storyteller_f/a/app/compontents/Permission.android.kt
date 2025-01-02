package com.storyteller_f.a.app.compontents

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference

val requestQueue = mutableStateListOf<Permission>()

@Composable
actual fun isPermissionGranted(permission: Permission): MutableState<Boolean> {
    val isGranted = remember {
        mutableStateOf(false)
    }
    val context = LocalContext.current
    LaunchedEffect(context, requestQueue) {
        isGranted.value = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    return isGranted
}


actual fun requestPermission(permission: Permission) {
    val launcher = launcherRef?.get() ?: return
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