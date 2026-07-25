package com.storyteller_f.a.client.asciidoc_parser

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class AsciidocPreviewTest {
    @Test
    fun buildsPreviewHtmlWithConvertedDocument() = kotlinx.coroutines.test.runTest {
        val html = buildAsciidocPreviewHtml("= Title\n\nHello, _AsciiDoc_ \"reader\"")

        assertContains(html, "<title>AsciiDoc Preview</title>")
        assertContains(html, "<h1>Title</h1>")
        assertContains(html, "Hello, <em>AsciiDoc</em> \"reader\"")
        assertFalse(html.contains("<script"))
    }
}
