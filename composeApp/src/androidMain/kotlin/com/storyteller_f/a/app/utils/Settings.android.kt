package com.storyteller_f.a.app.utils

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import com.storyteller_f.shared.contextRef

actual val defaultSettings: Settings by lazy {
    val context = contextRef.get()!!
    SharedPreferencesSettings(context.getSharedPreferences("default", Context.MODE_PRIVATE))
}
