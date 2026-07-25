package com.storyteller_f.a.client.compose_core.components

import android.content.Intent
import androidx.core.content.FileProvider
import com.storyteller_f.shared.getAppContextRefValue
import java.io.File

actual suspend fun openAsciidocPreviewHtml(html: String): Result<Unit> = runCatching {
    val context = getAppContextRefValue() ?: error("Android context is not available")
    val previewDirectory = File(context.cacheDir, "asciidoc-previews").apply {
        check(exists() || mkdirs()) { "Failed to create AsciiDoc preview directory" }
    }
    val previewFile = File.createTempFile("asciidoc-preview-", ".html", previewDirectory)
    previewFile.bufferedWriter().use { writer ->
        writer.write(html)
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.asciidoc-preview",
        previewFile,
    )
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "text/html")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(intent)
}
