package com.storyteller_f.a.app.core.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.storyteller_f.a.app.core.components.setText
import com.storyteller_f.a.app.core.components.LocalToaster
import dev.zt64.compose.pdf.RemotePdfState
import dev.zt64.compose.pdf.component.PdfPage
import kotlinx.coroutines.launch
import java.net.URI

@Composable
fun PdfViewBlock(url: String) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .heightIn(max = 300.dp)
            .clip(shape)
            .then(Modifier)
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
    ) {
        PdfView(url)
        val toasterState = LocalToaster.current
        val scope = rememberCoroutineScope()
        FlowRow {
            val clipboardManager = LocalClipboard.current
            IconButton({
                scope.launch {
                    clipboardManager.setText(url)
                    toasterState.showMessage("copied")
                }
            }) {
                Icon(Icons.Default.ContentCopy, "copy list")
            }
            val uriHandler = LocalUriHandler.current
            IconButton({
                uriHandler.openUri(url)
            }) {
                Icon(Icons.Default.Download, "download")
            }
        }
    }
}

@Composable
fun ColumnScope.PdfView(url: String) {
    val errorIndicator = rememberVectorPainter(Icons.Default.Error)
    val refreshIndicator = rememberVectorPainter(Icons.Default.Refresh)
    val state = remember(url, errorIndicator, refreshIndicator) {
        RemotePdfState(URI.create(url).toURL(), errorIndicator, refreshIndicator)
    }
    HorizontalPager(
        state = rememberPagerState { state.pageCount },
        modifier = Modifier.weight(1f)
    ) { i ->
        PdfPage(state = state, index = i)
    }
}
