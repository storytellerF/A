package com.storyteller_f.a.client.compose_core.components

import androidx.compose.runtime.*

@Composable
actual fun isPermissionGranted(permission: Permission): MutableState<Boolean> {
    return remember {
        mutableStateOf(true)
    }
}

actual fun requestPermission(permission: Permission) = Unit
