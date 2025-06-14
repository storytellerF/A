package com.storyteller_f.a.app.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import com.storyteller_f.shared.contextRef
import com.strabled.composepreferences.utilis.DataStoreManager
import okio.Path.Companion.toOkioPath

actual fun createSettings(name: String): Settings {
    val context = contextRef.get()!!
    return SharedPreferencesSettings(context.getSharedPreferences(name, Context.MODE_PRIVATE))
}

private val store by lazy {
    val context = contextRef.get()!!
    DataStoreManager(
        PreferenceDataStoreFactory.createWithPath(
            produceFile = {
                context.filesDir.resolve("main.preferences_pb").toOkioPath()
            }
        )
    )
}

@Composable
actual fun createCustomDataStoreManager(): DataStoreManager {
    return store
}
