package com.storyteller_f.a.app.core.utils

import com.russhwolf.settings.Settings
import com.russhwolf.settings.StorageSettings

actual fun buildSessionHistoryFactory(settings: Settings): SessionHistoryManager {
    return DefaultSessionHistoryManager(settings)
}

actual fun createSettings(name: String): Settings {
    return PrefixedSettings(StorageSettings(), name)
}

actual fun readInjectedSessionFromPrivateStorageOrNull(): ConvertedRawUserPassInfo? = null

private class PrefixedSettings(private val delegate: Settings, prefix: String) : Settings {
    private val keyPrefix = "$prefix."
    private fun String.k() = keyPrefix + this
    private fun String.strip() = removePrefix(keyPrefix)

    override val keys: Set<String>
        get() = delegate.keys.filter { it.startsWith(keyPrefix) }.map { it.strip() }.toSet()
    override val size: Int get() = keys.size
    override fun clear() = delegate.keys.filter { it.startsWith(keyPrefix) }.forEach(delegate::remove)
    override fun remove(key: String) = delegate.remove(key.k())
    override fun hasKey(key: String): Boolean = delegate.hasKey(key.k())
    override fun putInt(key: String, value: Int) = delegate.putInt(key.k(), value)
    override fun getInt(key: String, defaultValue: Int): Int = delegate.getInt(key.k(), defaultValue)
    override fun getIntOrNull(key: String): Int? = delegate.getIntOrNull(key.k())
    override fun putLong(key: String, value: Long) = delegate.putLong(key.k(), value)
    override fun getLong(key: String, defaultValue: Long): Long = delegate.getLong(key.k(), defaultValue)
    override fun getLongOrNull(key: String): Long? = delegate.getLongOrNull(key.k())
    override fun putString(key: String, value: String) = delegate.putString(key.k(), value)
    override fun getString(key: String, defaultValue: String): String = delegate.getString(key.k(), defaultValue)
    override fun getStringOrNull(key: String): String? = delegate.getStringOrNull(key.k())
    override fun putFloat(key: String, value: Float) = delegate.putFloat(key.k(), value)
    override fun getFloat(key: String, defaultValue: Float): Float = delegate.getFloat(key.k(), defaultValue)
    override fun getFloatOrNull(key: String): Float? = delegate.getFloatOrNull(key.k())
    override fun putDouble(key: String, value: Double) = delegate.putDouble(key.k(), value)
    override fun getDouble(key: String, defaultValue: Double): Double = delegate.getDouble(key.k(), defaultValue)
    override fun getDoubleOrNull(key: String): Double? = delegate.getDoubleOrNull(key.k())
    override fun putBoolean(key: String, value: Boolean) = delegate.putBoolean(key.k(), value)
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = delegate.getBoolean(key.k(), defaultValue)
    override fun getBooleanOrNull(key: String): Boolean? = delegate.getBooleanOrNull(key.k())
}
