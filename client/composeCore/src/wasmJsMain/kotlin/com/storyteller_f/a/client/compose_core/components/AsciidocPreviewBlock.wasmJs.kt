package com.storyteller_f.a.client.compose_core.components

import com.storyteller_f.a.client.asciidoc_parser.buildAsciidocPreviewDataUri
import kotlinx.browser.window

actual suspend fun openAsciidocPreviewHtml(html: String): Result<Unit> = runCatching {
    window.open(buildAsciidocPreviewDataUri(html), "_blank")
}
