package androidx.paging.compose

import androidx.compose.ui.platform.AndroidUiDispatcher
import kotlin.coroutines.CoroutineContext

actual val pagingMainDispatcher: CoroutineContext = AndroidUiDispatcher.Main
