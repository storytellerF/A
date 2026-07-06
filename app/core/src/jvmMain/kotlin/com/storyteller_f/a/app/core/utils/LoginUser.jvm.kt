package com.storyteller_f.a.app.core.utils

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import io.github.aakira.napier.Napier
import kotlinx.serialization.json.Json
import java.io.File

actual fun buildSessionHistoryFactory(settings: Settings): SessionHistoryManager {
    return DefaultSessionHistoryManager(settings)
}

actual fun createSettings(name: String): Settings {
    return PreferencesSettings.Factory().create(name)
}

actual fun readInjectedSessionFromPrivateStorageOrNull(): ConvertedRawUserPassInfo? {
    val sessionFilePath = System.getProperty("appium.session.file") ?: run {
        System.err.println("APP_DESKTOP_SESSION_FILE path=null")
        return null
    }
    val file = File(sessionFilePath)
    if (!file.exists()) {
        System.err.println("APP_DESKTOP_SESSION_FILE path=${file.canonicalPath} exists=false")
        Napier.d("Injected session file does not exist: $sessionFilePath")
        return null
    }
    return runCatching {
        val injected = Json.decodeFromString(ConvertedRawUserPassInfo.serializer(), file.readText())
        System.err.println(
            "APP_DESKTOP_SESSION_FILE path=${file.canonicalPath} exists=true address=${injected.address.take(8)}"
        )
        injected
    }.getOrElse { throwable ->
        throw IllegalStateException(
            "Injected session file exists but cannot be loaded: $sessionFilePath",
            throwable
        )
    }
}
