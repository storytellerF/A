package com.storyteller_f.a.app.compontents

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState

sealed interface Permission {
    data object Audio : Permission
}

@Composable
expect fun isPermissionGranted(permission: Permission): MutableState<Boolean>

expect fun requestPermission(permission: Permission)
