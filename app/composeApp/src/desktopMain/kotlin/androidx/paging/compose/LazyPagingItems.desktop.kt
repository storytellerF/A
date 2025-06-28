package androidx.paging.compose

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlin.coroutines.CoroutineContext

actual val pagingMainDispatcher: CoroutineContext = Dispatchers.Swing
