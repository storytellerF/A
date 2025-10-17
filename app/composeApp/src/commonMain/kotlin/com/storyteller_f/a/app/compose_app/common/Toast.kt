package com.storyteller_f.a.app.compose_app.common

import com.dokar.sonner.ToasterState
import kotlin.time.Duration.Companion.seconds

interface Toast {
    fun showMessage(message: String)
}

class Sonner(val toasterState: ToasterState) : Toast {
    override fun showMessage(message: String) {
        toasterState.show(message, duration = 1.seconds)
    }
}
