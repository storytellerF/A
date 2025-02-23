package com.storyteller_f.a.app.utils

actual fun buildLoginUserSessionFactory(): LoginUserSessionManager {
    return DefaultLoginUserSessionManager()
}
