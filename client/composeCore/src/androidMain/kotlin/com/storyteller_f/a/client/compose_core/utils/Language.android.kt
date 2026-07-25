package com.storyteller_f.a.client.compose_core.utils

import java.util.*

actual fun getCurrentLanguage(): String {
    return Locale.getDefault().getDisplayLanguage(Locale.CHINESE)
}
