package com.storyteller_f.a.app.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import com.storyteller_f.shared.contextRef
import com.strabled.composepreferences.utilis.DataStoreManager
import okio.Path.Companion.toOkioPath

actual val defaultSettings: Settings by lazy {
    val context = contextRef.get()!!
    SharedPreferencesSettings(context.getSharedPreferences("default", Context.MODE_PRIVATE))
}

@Composable
actual fun customDataStoreManager(): DataStoreManager {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return DataStoreManager(
        PreferenceDataStoreFactory.createWithPath(
            scope = scope,
            produceFile = {
                context.filesDir.resolve("main.preferences_pb").toOkioPath()
            }
        )
    )
}
