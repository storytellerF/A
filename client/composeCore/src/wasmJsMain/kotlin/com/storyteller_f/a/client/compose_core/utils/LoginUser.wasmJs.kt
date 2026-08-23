/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.client.compose_core.utils

import com.russhwolf.settings.Settings
import com.russhwolf.settings.StorageSettings
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.serialization.json.Json

actual fun buildSessionHistoryFactory(settings: Settings): SessionHistoryManager =
    DefaultSessionHistoryManager(
    settings,
)

actual fun createSettings(name: String): Settings = PrefixedSettings(StorageSettings(), name)

actual fun readInjectedSessionFromPrivateStorageOrNull(): ConvertedRawUserPassInfo? {
    val serializedSession = localStorage.getItem(APPIUM_INJECTED_SESSION_KEY) ?: return null
    val query = window.location.search
    if (!query.contains("appium=true")) return null
    return Json.decodeFromString(ConvertedRawUserPassInfo.serializer(), serializedSession)
}

private class PrefixedSettings(private val delegate: Settings, prefix: String) : Settings {
    private val keyPrefix = "$prefix."
    private fun String.storageKey() = keyPrefix + this
    private fun String.strip() = removePrefix(keyPrefix)

    override val keys: Set<String>
        get() = delegate.keys.filter { it.startsWith(keyPrefix) }.map { it.strip() }.toSet()
    override val size: Int get() = keys.size
    override fun clear() = delegate.keys.filter { it.startsWith(keyPrefix) }.forEach(delegate::remove)
    override fun remove(key: String) = delegate.remove(key.storageKey())
    override fun hasKey(key: String): Boolean = delegate.hasKey(key.storageKey())
    override fun putInt(key: String, value: Int) = delegate.putInt(key.storageKey(), value)
    override fun getInt(key: String, defaultValue: Int): Int = delegate.getInt(key.storageKey(), defaultValue)
    override fun getIntOrNull(key: String): Int? = delegate.getIntOrNull(key.storageKey())
    override fun putLong(key: String, value: Long) = delegate.putLong(key.storageKey(), value)
    override fun getLong(key: String, defaultValue: Long): Long = delegate.getLong(key.storageKey(), defaultValue)
    override fun getLongOrNull(key: String): Long? = delegate.getLongOrNull(key.storageKey())
    override fun putString(key: String, value: String) = delegate.putString(key.storageKey(), value)
    override fun getString(key: String, defaultValue: String): String =
        delegate.getString(
        key.storageKey(),
        defaultValue,
    )
    override fun getStringOrNull(key: String): String? = delegate.getStringOrNull(key.storageKey())
    override fun putFloat(key: String, value: Float) = delegate.putFloat(key.storageKey(), value)
    override fun getFloat(key: String, defaultValue: Float): Float = delegate.getFloat(key.storageKey(), defaultValue)
    override fun getFloatOrNull(key: String): Float? = delegate.getFloatOrNull(key.storageKey())
    override fun putDouble(key: String, value: Double) = delegate.putDouble(key.storageKey(), value)
    override fun getDouble(key: String, defaultValue: Double): Double =
        delegate.getDouble(
        key.storageKey(),
        defaultValue,
    )
    override fun getDoubleOrNull(key: String): Double? = delegate.getDoubleOrNull(key.storageKey())
    override fun putBoolean(key: String, value: Boolean) = delegate.putBoolean(key.storageKey(), value)
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        delegate.getBoolean(
        key.storageKey(),
        defaultValue,
    )
    override fun getBooleanOrNull(key: String): Boolean? = delegate.getBooleanOrNull(key.storageKey())
}

private const val APPIUM_INJECTED_SESSION_KEY = "appium.injected_session"
