package com.storyteller_f.a.app.utils

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.WebLocalStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.stringPreferencesKey
import com.storyteller_f.a.app.AppConfig
import com.storyteller_f.a.app.pages.HOME_START_DESTINATION_PREFERENCE_KEY
import dev.jordond.connectivity.Connectivity
import dev.jordond.connectivity.ConnectivityProvider
import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json

actual val appPlatform: AppPlatform
    get() = AppPlatform(
        hasNativeBack = false,
        debug = false,
    )

actual fun initEnvironment(context: Any) = Unit

actual fun createConnectivity(): Connectivity {
    return Connectivity(ConnectivityProvider(flowOf(Connectivity.Status.Connected(metered = false))))
}

actual fun getClientFile(path: String): ClientFile? = null

private const val APP_PREFERENCES_STORE_NAME = "main.preferences_pb"
private const val GPT_MODEL_PREFERENCE_KEY = "gpt_model"
private val legacyPreferenceKeys = listOf(
    GPT_MODEL_PREFERENCE_KEY,
    HOME_START_DESTINATION_PREFERENCE_KEY,
)

private val appPreferencesDataStore by lazy {
    DataStoreFactory.create(
        storage = WebLocalStorage(
            serializer = PreferencesSerializer,
            name = APP_PREFERENCES_STORE_NAME,
        ),
        migrations = listOf(LegacyComposePreferencesMigration()),
    )
}

internal actual fun createAppPreferencesDataStore(): DataStore<Preferences> = appPreferencesDataStore

private class LegacyComposePreferencesMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        return legacyPreferenceKeys.any { key ->
            currentData[stringPreferencesKey(key)] == null && localStorage.getItem(key) != null
        }
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        return currentData.toMutablePreferences().apply {
            legacyPreferenceKeys.forEach { key ->
                val preferenceKey = stringPreferencesKey(key)
                localStorage.getItem(key)?.takeIf { this[preferenceKey] == null }?.let { serializedValue ->
                    this[preferenceKey] = Json.decodeFromString<String>(serializedValue)
                }
            }
        }
    }

    override suspend fun cleanUp() {
        legacyPreferenceKeys.forEach(localStorage::removeItem)
    }
}

actual fun unregisterPushService() = Unit

actual fun getDeepLinkHost(): String = AppConfig.DEEP_LINK_HOST

actual fun getDeepLinkScheme(): String = AppConfig.DEEP_LINK_SCHEME_PREFIX
