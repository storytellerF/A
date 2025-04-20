package com.storyteller_f.a.app.utils

import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import com.strabled.composepreferences.utilis.DataStoreManager
import io.github.vinceglb.filekit.utils.toFile
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import okio.Path.Companion.toOkioPath
import java.util.prefs.Preferences

actual val defaultSettings: Settings
    get() = PreferencesSettings(Preferences.userRoot())

private val store by lazy {
    DataStoreManager(
        PreferenceDataStoreFactory.createWithPath(
            produceFile = {
                val pb = SystemFileSystem.resolve(Path(SystemTemporaryDirectory, "main.preferences_pb"))
                pb.toFile().toOkioPath()
            }
        )
    )
}

@Composable
actual fun customDataStoreManager(): DataStoreManager {
    return store
}
