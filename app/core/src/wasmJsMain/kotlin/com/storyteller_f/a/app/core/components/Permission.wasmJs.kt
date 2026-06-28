package com.storyteller_f.a.app.core.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
actual fun isPermissionGranted(permission: Permission): MutableState<Boolean> {
    return remember { mutableStateOf(true) }
}

actual fun requestPermission(permission: Permission) = Unit
