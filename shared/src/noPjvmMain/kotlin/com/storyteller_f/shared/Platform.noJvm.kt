package com.storyteller_f.shared

import io.github.aakira.napier.Antilog
import io.github.aakira.napier.DebugAntilog

actual val logger: Antilog
    get() = DebugAntilog()