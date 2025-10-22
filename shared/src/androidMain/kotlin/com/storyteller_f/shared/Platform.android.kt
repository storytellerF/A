package com.storyteller_f.shared

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import java.io.File
import java.lang.ref.WeakReference
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

lateinit var appContextRef: WeakReference<Application>

fun getAppContextRefValue(): Application? {
    return appContextRef.get()
}

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"

    @OptIn(ExperimentalUuidApi::class)
    val currentId by lazy {
        Uuid.random().toHexString()
    }

    @get:SuppressLint("HardwareIds")
    override val id: String
        get() {
            val file = File("/data/local/tmp/a/id")
            file.parentFile!!.mkdirs()
            file.createNewFile()
            file.writeText(currentId)
            return currentId
        }

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
