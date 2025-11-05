package com.storyteller_f.a.panel

import android.app.Application
import android.os.StrictMode
import com.storyteller_f.shared.appContextRef
import com.storyteller_f.shared.loadCryptoLibIfNeed
import com.storyteller_f.shared.setupKmpLogger
import java.lang.ref.WeakReference

class PanelApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        setupKmpLogger()
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder(StrictMode.getVmPolicy())
                .detectLeakedClosableObjects()
                .build()
        )
        appContextRef = WeakReference(this)
        loadCryptoLibIfNeed()
    }
}
