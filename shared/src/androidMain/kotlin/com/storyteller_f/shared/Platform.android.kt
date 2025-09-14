package com.storyteller_f.shared

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import android.provider.Settings
import java.lang.ref.WeakReference

lateinit var appContextRef: WeakReference<Application>

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"

    @get:SuppressLint("HardwareIds")
    override val id: String
        get() = Settings.Secure.getString(appContextRef.get()!!.contentResolver, Settings.Secure.ANDROID_ID)
}

actual fun getPlatform(): Platform = AndroidPlatform()

val isRunningOnRobolectric: Boolean
    get() = try {
        Class.forName("org.robolectric.Robolectric")
        true
    } catch (e: Exception) {
        e
        false
    }
