package com.storyteller_f.a.app.compose_app.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import com.storyteller_f.shared.appContextRef
import com.strabled.composepreferences.utilis.DataStoreManager
import okio.Path.Companion.toOkioPath

actual fun createSettings(name: String): Settings {
    val context = appContextRef.get()!!
    return SharedPreferencesSettings(context.getSharedPreferences(name, Context.MODE_PRIVATE))
}

