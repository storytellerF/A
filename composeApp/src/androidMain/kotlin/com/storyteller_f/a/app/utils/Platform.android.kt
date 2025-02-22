package com.storyteller_f.a.app.utils

import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import com.storyteller_f.a.app.compontents.mainAppRef
import com.storyteller_f.a.app.initFromContext

actual val platform: Platform
    get() {
        val currentState = mainAppRef?.get()?.lifecycle?.currentState
        val isActive = currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
        return Platform(true, isActive)
    }

actual fun initEnvironment(context: Any) {
    if (context is ComponentActivity) {
        context.initFromContext()
    }
}
