package com.storyteller_f.shared

import android.app.Application
import android.os.Build
import java.lang.ref.WeakReference

lateinit var appContextRef: WeakReference<Application>

fun getAppContextRefValue(): Application? {
    return appContextRef.get()
}

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"

}

actual fun getPlatform(): Platform = AndroidPlatform()

val isRunningOnRobolectric: Boolean
    get() = try {
        Class.forName("org.robolectric.Robolectric")
        true
    } catch (_: Exception) {
        false
    }
