package com.storyteller_f.a.app.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.storyteller_f.shared.model.FileInfo
import kotlinx.coroutines.launch

@Composable
fun PdfViewBlock(fileInfo: FileInfo, onClick: (FileInfo) -> Unit) {
    val url = fileInfo.url
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .clickable { onClick(fileInfo) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PictureAsPdf, "pdf")
            Text(
                fileInfo.name,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            )
            IconButton({
                onClick(fileInfo)
            }) {
                Icon(Icons.Default.Fullscreen, "fullscreen")
            }
        }
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

// 渲染远程 PDF。jvm/android 用 compose-pdf；wasm 无对应 Compose PDF 库，
// 仅提供「在浏览器打开」按钮（见各平台 actual）。
@Composable
expect fun PdfView(url: String, modifier: Modifier)
