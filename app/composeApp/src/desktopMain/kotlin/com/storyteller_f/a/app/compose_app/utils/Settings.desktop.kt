package com.storyteller_f.a.app.compose_app.utils

import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import com.strabled.composepreferences.utilis.DataStoreManager
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.utils.toFile
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import okio.Path.Companion.toOkioPath

actual fun createSettings(name: String): Settings {
    return PreferencesSettings.Factory().create(name)
}

private val store by lazy {
    DataStoreManager(
        PreferenceDataStoreFactory.createWithPath(
            produceFile = {
                val pb = Path(SystemTemporaryDirectory, "com.storyteller_f.a.app.compose_app.main.preferences_pb")
                val file = pb.toFile()
                if (!file.exists() && !file.createNewFile()) {
                    Napier.e {
                        "$file create failed"
                    }
                }
                file.toOkioPath()
            }
        )
    )
}

@Composable
actual fun createCustomDataStoreManager(): DataStoreManager {
    return store
}
