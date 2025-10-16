package com.storyteller_f.a.app.compose_app.utils

import com.russhwolf.settings.Settings

actual fun buildLoginHistoryFactory(settings: Settings): LoginHistoryManager {
    return DefaultLoginHistoryManager(settings)
}

