package com.storyteller_f.a.app

import android.app.Application
import com.storyteller_f.shared.addProvider

class AApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        addProvider()
        restoreFromStorage()
    }
}
