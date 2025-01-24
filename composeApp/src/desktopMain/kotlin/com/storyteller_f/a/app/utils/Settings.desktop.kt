package com.storyteller_f.a.app.utils

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.util.prefs.Preferences

actual val defaultSettings: Settings
    get() = PreferencesSettings(Preferences.userRoot())