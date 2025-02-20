package com.storyteller_f.a.app.utils

import androidx.lifecycle.Lifecycle
import com.storyteller_f.a.app.compontents.mainAppRef

actual val platform: Platform
    get() {
        val currentState = mainAppRef?.get()?.lifecycle?.currentState
        val isActive = currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
        return Platform(true, isActive)
    }
