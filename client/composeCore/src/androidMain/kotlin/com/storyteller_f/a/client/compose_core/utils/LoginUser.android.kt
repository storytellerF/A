/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.client.compose_core.utils

import com.storyteller_f.shared.getAppContextRefValue
import io.github.aakira.napier.Napier
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream

private val injectedSessionJson = Json { ignoreUnknownKeys = true }

actual fun readInjectedSessionFromPrivateStorageOrNull(): ConvertedRawUserPassInfo? {
    val context = getAppContextRefValue() ?: return null
    val injectedFile =
        context.filesDir
            .resolve("appium-session")
            .resolve("session.json")
    if (!injectedFile.exists()) {
        Napier.d("Injected session file does not exist: ${injectedFile.absolutePath}")
        return null
    }

    return runCatching {
        val content = BufferedInputStream(injectedFile.inputStream()).use { it.readBytes().decodeToString() }
        injectedSessionJson.decodeFromString(ConvertedRawUserPassInfo.serializer(), content)
    }.getOrElse { throwable ->
        throw IllegalStateException(
            "Injected session file exists but cannot be loaded: ${injectedFile.absolutePath}",
            throwable,
        )
    }
}
