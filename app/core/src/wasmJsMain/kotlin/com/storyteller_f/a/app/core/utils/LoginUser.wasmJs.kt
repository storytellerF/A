package com.storyteller_f.a.app.core.utils

import com.russhwolf.settings.Settings
import com.russhwolf.settings.StorageSettings

actual fun buildSessionHistoryFactory(settings: Settings): SessionHistoryManager {
    return DefaultSessionHistoryManager(settings)
}

actual fun createSettings(name: String): Settings {
    return StorageSettings()
}

actual fun readInjectedSessionFromPrivateStorageOrNull(): ConvertedRawUserPassInfo? = null
