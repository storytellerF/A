package com.storyteller_f.a.app.compose_app.utils

import java.util.*

actual fun getCurrentLanguage(): String {
    return Locale.getDefault().getDisplayLanguage(Locale.CHINESE)
}
