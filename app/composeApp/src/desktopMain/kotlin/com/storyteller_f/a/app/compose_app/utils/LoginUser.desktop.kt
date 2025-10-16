package com.storyteller_f.a.app.compose_app.utils

import com.russhwolf.settings.Settings

actual fun buildLoginUserSessionFactory(settings: Settings): LoginUserSessionManager {
    return DefaultLoginUserSessionManager(settings)
}

