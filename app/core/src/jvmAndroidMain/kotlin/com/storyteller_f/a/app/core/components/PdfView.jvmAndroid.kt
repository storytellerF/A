package com.storyteller_f.a.app.core.components

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import dev.zt64.compose.pdf.RemotePdfState
import dev.zt64.compose.pdf.component.PdfPage
import java.net.URI

@Composable
actual fun PdfView(url: String, modifier: Modifier) {
    val errorIndicator = rememberVectorPainter(Icons.Default.Error)
    val refreshIndicator = rememberVectorPainter(Icons.Default.Refresh)
    val state = remember(url, errorIndicator, refreshIndicator) {
        RemotePdfState(URI.create(url).toURL(), errorIndicator, refreshIndicator)
    }
    HorizontalPager(
        state = rememberPagerState { state.pageCount },
        modifier = modifier
    ) { i ->
        PdfPage(state = state, index = i)
    }
}
