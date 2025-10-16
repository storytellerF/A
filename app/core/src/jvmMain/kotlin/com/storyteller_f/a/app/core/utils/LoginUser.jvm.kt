package com.storyteller_f.a.app.core.utils

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings

actual fun buildLoginHistoryFactory(settings: Settings): LoginHistoryManager {
    return DefaultLoginHistoryManager(settings)
}

actual fun createSettings(name: String): Settings {
    return PreferencesSettings.Factory().create(name)
}

