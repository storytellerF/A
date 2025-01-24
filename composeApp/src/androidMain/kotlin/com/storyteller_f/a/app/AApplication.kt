package com.storyteller_f.a.app

import android.app.Application
import com.storyteller_f.a.app.utils.contextRef
import com.storyteller_f.a.app.utils.restoreFromStorage
import com.storyteller_f.crypto_jvm.addProviderForAndroid
import java.lang.ref.WeakReference

class AApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        contextRef = WeakReference(this)
        addProviderForAndroid()
        restoreFromStorage()
    }
}
