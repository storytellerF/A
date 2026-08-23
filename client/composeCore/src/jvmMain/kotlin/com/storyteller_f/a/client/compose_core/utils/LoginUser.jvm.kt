/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.client.compose_core.utils

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import io.github.aakira.napier.Napier
import kotlinx.serialization.json.Json
import java.io.File

actual fun buildSessionHistoryFactory(settings: Settings): SessionHistoryManager =
    DefaultSessionHistoryManager(
    settings,
)

actual fun createSettings(name: String): Settings = PreferencesSettings.Factory().create(name)

actual fun readInjectedSessionFromPrivateStorageOrNull(): ConvertedRawUserPassInfo? {
    val sessionFilePath =
        System.getProperty("appium.session.file") ?: run {
            Napier.d { "Injected desktop session path is not configured" }
            return null
        }
    val file = File(sessionFilePath)
    if (!file.exists()) {
        Napier.d { "Injected session file does not exist" }
        return null
    }
    return runCatching {
        val injected = Json.decodeFromString(ConvertedRawUserPassInfo.serializer(), file.readText())
        Napier.d { "Injected desktop session loaded" }
        injected
    }.getOrElse { throwable ->
        throw IllegalStateException(
            "Injected session file exists but cannot be loaded: $sessionFilePath",
            throwable,
        )
    }
}
