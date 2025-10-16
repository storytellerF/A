package org.storyteller_f.a.cloud.panel

import android.app.Application
import android.os.StrictMode
import com.storyteller_f.shared.appContextRef
import com.storyteller_f.shared.kmpLogger
import com.storyteller_f.shared.loadCryptoLibIfNeed
import io.github.aakira.napier.Napier
import java.lang.ref.WeakReference

class PanelApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Napier.base(kmpLogger)
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder(StrictMode.getVmPolicy())
                .detectLeakedClosableObjects()
                .build()
        )
        appContextRef = WeakReference(this)
        loadCryptoLibIfNeed()
    }
}
