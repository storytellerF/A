package com.storyteller_f.a.app.compontents

import androidx.compose.runtime.*

@Composable
actual fun isPermissionGranted(permission: Permission): MutableState<Boolean> {
    return remember {
        mutableStateOf(true)
    }
}

actual fun requestPermission(permission: Permission) = Unit
