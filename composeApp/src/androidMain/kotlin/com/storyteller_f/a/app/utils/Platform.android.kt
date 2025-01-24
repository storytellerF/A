package com.storyteller_f.a.app.utils

import android.content.Context
import java.lang.ref.WeakReference

lateinit var contextRef: WeakReference<Context>

actual val platform: Platform
    get() = Platform(true)
