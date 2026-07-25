package com.storyteller_f.a.client.asciidoc_parser

import kotlin.test.Test
import kotlin.test.assertContains

class AsciidocPreviewTest {
    @Test
    fun buildsPreviewHtmlWithEscapedSource() {
        val html = buildAsciidocPreviewHtml("= Title\n\nHello, _AsciiDoc_ \"reader\"")

        assertContains(html, "<title>AsciiDoc Preview</title>")
        assertContains(html, "Hello, _AsciiDoc_ \\\"reader\\\"")
        assertContains(html, "asciidoctor.convert(source")
    }

    @Test
    fun buildsPreviewDataUri() {
        val uri = buildAsciidocPreviewDataUri("<html>Hello, AsciiDoc</html>")

        assertContains(uri, "data:text/html;charset=utf-8,")
        assertContains(uri, "%3Chtml%3EHello%2C%20AsciiDoc%3C%2Fhtml%3E")
    }
}
