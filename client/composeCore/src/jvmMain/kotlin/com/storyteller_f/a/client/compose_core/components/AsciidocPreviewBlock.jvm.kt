package com.storyteller_f.a.client.compose_core.components

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.nio.file.Files

actual suspend fun openAsciidocPreviewHtml(html: String): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val file = Files.createTempFile("asciidoc-preview-", ".html")
        Files.newBufferedWriter(file).use { writer ->
            writer.write(html)
        }
        if (System.getProperty("os.name").lowercase().contains("linux")) {
            ProcessBuilder("xdg-open", file.toUri().toString()).start()
        } else {
            check(Desktop.isDesktopSupported()) {
                "Desktop browser integration is not supported"
            }
            Desktop.getDesktop().browse(file.toUri())
        }
        Unit
    }
}
