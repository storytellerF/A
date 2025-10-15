package com.storyteller_f.a.app.core.common

import androidx.lifecycle.ViewModel
import com.storyteller_f.a.client.core.LoadingHandler

abstract class SimpleViewModel<T : Any> : ViewModel() {
    abstract val handler: LoadingHandler<T>
}
