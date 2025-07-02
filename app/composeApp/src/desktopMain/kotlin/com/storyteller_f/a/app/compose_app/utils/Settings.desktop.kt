package com.storyteller_f.a.app.compose_app.utils

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

actual fun createSettings(name: String): Settings {
    return PreferencesSettings.Factory().create(name)
}

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
actual fun createCustomDataStoreManager(): DataStoreManager {
    return store
}
