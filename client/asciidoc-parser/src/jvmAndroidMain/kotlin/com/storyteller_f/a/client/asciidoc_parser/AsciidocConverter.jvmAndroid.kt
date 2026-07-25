package com.storyteller_f.a.client.asciidoc_parser

import com.caoccao.javet.interop.NodeRuntime
import com.caoccao.javet.interop.V8Host
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.use

actual suspend fun convertAsciidoc(source: String, asciidoctorScript: String): String =
    withContext(Dispatchers.Default) {
        (V8Host.getNodeInstance().createV8Runtime() as NodeRuntime).use { runtime ->
            runtime.getExecutor(asciidoctorScript).executeVoid()
            runtime.getExecutor(
                "module\$build\$asciidoctor_browser.default().convert(" +
                    "${source.toJsStringLiteral()}, { safe: 'safe', attributes: { showtitle: true } })"
            ).executeString()
        }
    }
