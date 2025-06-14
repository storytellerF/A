package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.ui.Modifier

@OptIn(ExperimentalLayoutApi::class)
actual fun Modifier.imeAnimation(): Modifier {
    return imeNestedScroll()
}
