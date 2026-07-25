package com.storyteller_f.a.client.compose_core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewStateWithHTMLData
import com.storyteller_f.a.client.asciidoc_parser.buildAsciidocPreviewHtml
import com.storyteller_f.shared.utils.readCodeFence
import kotlinx.coroutines.launch

@Composable
fun AsciidocPreviewBlock(modal: MarkdownComponentModel) {
    val source = remember(modal.node, modal.content) {
        readCodeFence(modal.node, modal.content)
    }
    val scope = rememberCoroutineScope()
    val toasterState = LocalToaster.current
    var previewHtml by remember { mutableStateOf<String?>(null) }
    fun openPreview() {
        scope.launch {
            previewHtml = buildAsciidocPreviewHtml(source)
        }
    }
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .clickable { openPreview() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.Article, "asciidoc")
            Text(
                "AsciiDoc preview",
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            )
            IconButton({ openPreview() }) {
                Icon(Icons.Default.Visibility, "open")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val clipboardManager = LocalClipboard.current
            IconButton({
                scope.launch {
                    clipboardManager.setText(source)
                    toasterState.showMessage("copied")
                }
            }) {
                Icon(Icons.Default.ContentCopy, "copy source")
            }
            Text(
                source.lineSequence().firstOrNull().orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    previewHtml?.let { preview ->
        AsciidocPreviewDialog(preview, onDismissRequest = { previewHtml = null })
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AsciidocPreviewDialog(html: String, onDismissRequest: () -> Unit) {
    val state = rememberWebViewStateWithHTMLData(data = html, mimeType = "text/html")
    state.webSettings.isJavaScriptEnabled = true
    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        Surface(modifier = Modifier.fillMaxWidth().fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("AsciiDoc preview") },
                        navigationIcon = {
                            IconButton(onClick = onDismissRequest) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "close preview")
                            }
                        },
                    )
                },
            ) { paddingValues ->
                WebView(
                    state = state,
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                )
            }
        }
    }
}
