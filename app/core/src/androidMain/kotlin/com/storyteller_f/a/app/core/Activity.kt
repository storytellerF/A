package com.storyteller_f.a.app.core

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowInsetsControllerCompat

fun ComponentActivity.commonForActivity() {
    enableEdgeToEdge()
    /**
     * 在Android 13 上必须加这一行
     */
    WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = false
}