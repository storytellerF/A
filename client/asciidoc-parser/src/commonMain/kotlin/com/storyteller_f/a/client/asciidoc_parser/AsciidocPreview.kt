package com.storyteller_f.a.client.asciidoc_parser

private const val ASCIIDOCTOR_JS_URL =
    "https://cdn.jsdelivr.net/npm/@asciidoctor/core@3.0.4/dist/browser/asciidoctor.min.js"

fun buildAsciidocPreviewHtml(source: String): String {
    val sourceLiteral = source.toJsStringLiteral()
    return """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>AsciiDoc Preview</title>
          <script src="$ASCIIDOCTOR_JS_URL"></script>
          <style>
            :root {
              color-scheme: light dark;
              font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
              line-height: 1.55;
            }
            body {
              box-sizing: border-box;
              max-width: 960px;
              margin: 0 auto;
              padding: 32px 24px;
            }
            pre {
              overflow-x: auto;
              padding: 16px;
              border-radius: 8px;
              background: color-mix(in srgb, CanvasText 8%, Canvas);
            }
            #error {
              color: #b00020;
              white-space: pre-wrap;
            }
          </style>
        </head>
        <body>
          <main id="preview"></main>
          <pre id="fallback"></pre>
          <pre id="error" hidden></pre>
          <script>
            const source = $sourceLiteral;
            document.getElementById('fallback').textContent = source;
            try {
              const asciidoctor = Asciidoctor();
              document.getElementById('preview').innerHTML = asciidoctor.convert(source, { safe: 'safe' });
              document.getElementById('fallback').hidden = true;
            } catch (error) {
              const errorNode = document.getElementById('error');
              errorNode.hidden = false;
              errorNode.textContent = error instanceof Error ? error.message : String(error);
            }
          </script>
        </body>
        </html>
    """.trimIndent()
}

fun buildAsciidocPreviewDataUri(html: String): String {
    val encoded = html.encodeToByteArray().joinToString("") { byte ->
        val value = byte.toInt() and 0xFF
        val char = value.toChar()
        if (char.isUriUnreserved()) {
            char.toString()
        } else {
            "%${value.toString(16).uppercase().padStart(2, '0')}"
        }
    }
    return "data:text/html;charset=utf-8,$encoded"
}

private fun Char.isUriUnreserved(): Boolean {
    return this in 'A'..'Z' ||
        this in 'a'..'z' ||
        this in '0'..'9' ||
        this == '-' ||
        this == '.' ||
        this == '_' ||
        this == '~'
}

private fun String.toJsStringLiteral(): String = buildString {
    append('"')
    for (char in this@toJsStringLiteral) {
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\u2028' -> append("\\u2028")
            '\u2029' -> append("\\u2029")
            else -> append(char)
        }
    }
    append('"')
}
