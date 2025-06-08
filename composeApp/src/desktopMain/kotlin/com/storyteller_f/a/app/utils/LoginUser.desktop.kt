package com.storyteller_f.a.app.utils

import com.russhwolf.settings.Settings

actual fun buildLoginUserSessionFactory(settings: Settings): LoginUserSessionManager {
    return DefaultLoginUserSessionManager(settings)
}

actual fun unregisterPushService() = Unit
