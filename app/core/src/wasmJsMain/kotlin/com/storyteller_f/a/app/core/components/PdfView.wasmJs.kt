package com.storyteller_f.a.app.core.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

// wasm 上没有可用的 Compose PDF 渲染库，回退为用浏览器原生 PDF 查看器打开。
@Composable
actual fun PdfView(url: String, modifier: Modifier) {
    val uriHandler = LocalUriHandler.current
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = { uriHandler.openUri(url) }) {
            Icon(Icons.Default.OpenInBrowser, contentDescription = null)
            Text("在浏览器打开 PDF", modifier = Modifier.padding(start = 8.dp))
        }
    }
}
