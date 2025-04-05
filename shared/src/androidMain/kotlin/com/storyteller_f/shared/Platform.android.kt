package com.storyteller_f.shared

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.DebugAntilog
import java.lang.ref.WeakReference

lateinit var contextRef: WeakReference<Context>

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"

    @SuppressLint("HardwareIds")
    override val id: String = Settings.Secure.getString(contextRef.get()!!.contentResolver, Settings.Secure.ANDROID_ID)
}

actual fun getPlatform(): Platform = AndroidPlatform()
actual val logger: Antilog
    get() = DebugAntilog()
