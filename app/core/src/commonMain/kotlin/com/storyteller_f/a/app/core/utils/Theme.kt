package com.storyteller_f.a.app.core.utils

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
expect fun getAppDynamicColorScheme(dynamicColor: Boolean, darkTheme: Boolean): ColorScheme?