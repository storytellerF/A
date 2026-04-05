package com.storyteller_f.a.app.core.utils

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings

actual fun buildSessionHistoryFactory(settings: Settings): SessionHistoryManager {
    return DefaultSessionHistoryManager(settings)
}

actual fun createSettings(name: String): Settings {
    return PreferencesSettings.Factory().create(name)
}

actual fun readInjectedSessionFromPrivateStorageOrNull(): ConvertedRawUserPassInfo? {
    return null
}
