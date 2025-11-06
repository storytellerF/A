package com.storyteller_f.a.cloud.server

import com.storyteller_f.a.backend.core.setLogPath
import com.storyteller_f.a.cloud.openpdf.OpenPdf
import com.storyteller_f.a.cloud.pdf.SnapshotGeneration
import com.storyteller_f.a.cloud.pdfbox.PdfBox
import com.storyteller_f.shared.CryptoJvm
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import org.apache.pdfbox.pdmodel.encryption.SecurityProvider
import org.intellij.lang.annotations.Language
import java.io.File
import java.security.Security
import kotlin.test.Test

class SnapshotTest {

    @Test
    fun `test generate signed pdf`() = testPdf {
        val password = "123456"
        val path = "build/test/keystore2.p12"
        CryptoJvm.createKeystore(password.toCharArray(), path)
        Security.addProvider(SecurityProvider.getProvider())
        listOf(OpenPdf(), PdfBox()).forEachIndexed { i, pdf ->
            val pdfFile = File("build/tmp/$i.pdf")
            val signedFile = File("build/tmp/$i.signed.pdf")
            pdf.generateSignedSnapshot(
                UserInfo.EMPTY,
                UserInfo.EMPTY,
                "hello world",
                TopicInfo.EMPTY,
                emptyMap(),
                SnapshotGeneration.KeyStoreGeneration(path, password, pdfFile, signedFile)
            ).getOrThrow()
        }
    }

    @Test
    fun `test openpdf generate code fence`() = testPdf {
        val pdfFile = File("build/tmp/code-fence.pdf")
        OpenPdf().generateSignedSnapshot(
            UserInfo.EMPTY,
            UserInfo.EMPTY,
            @Language("kotlin") """```kotlin
                |fun main() {
                |    println("hello world")
                |}
                |```""".trimMargin(),
            TopicInfo.EMPTY,
            emptyMap(),
            SnapshotGeneration.SimpleGeneration(pdfFile)
        ).getOrThrow()
    }

    @Test
    fun `test openpdf generate headings`() = testPdf {
        val pdfFile = File("build/tmp/headings.pdf")
        val content = """
            # Heading 1
            ## Heading 2
            Normal paragraph

            Heading A
            ===

            Heading B
            ---
        """.trimIndent()
        OpenPdf().generateSignedSnapshot(
            UserInfo.EMPTY,
            UserInfo.EMPTY,
            content,
            TopicInfo.EMPTY,
            emptyMap(),
            SnapshotGeneration.SimpleGeneration(pdfFile)
        ).getOrThrow()
    }

    @Test
    fun `test openpdf generate emphasis and strong`() = testPdf {
        val pdfFile = File("build/tmp/emph-strong.pdf")
        val content = """
            *italic* and **bold** text with normal content.
        """.trimIndent()
        OpenPdf().generateSignedSnapshot(
            UserInfo.EMPTY,
            UserInfo.EMPTY,
            content,
            TopicInfo.EMPTY,
            emptyMap(),
            SnapshotGeneration.SimpleGeneration(pdfFile)
        ).getOrThrow()
    }

    @Test
    fun `test openpdf generate code span`() = testPdf {
        val pdfFile = File("build/tmp/code-span.pdf")
        val content = """
            Inline `code` span inside a sentence.
        """.trimIndent()
        OpenPdf().generateSignedSnapshot(
            UserInfo.EMPTY,
            UserInfo.EMPTY,
            content,
            TopicInfo.EMPTY,
            emptyMap(),
            SnapshotGeneration.SimpleGeneration(pdfFile)
        ).getOrThrow()
    }

    @Test
    fun `test openpdf generate lists`() = testPdf {
        val pdfFile = File("build/tmp/lists.pdf")
        val content = """
            - item 1
              - nested item 1.1
            - item 2

            1. first
            2. second
               1. sub first
        """.trimIndent()
        OpenPdf().generateSignedSnapshot(
            UserInfo.EMPTY,
            UserInfo.EMPTY,
            content,
            TopicInfo.EMPTY,
            emptyMap(),
            SnapshotGeneration.SimpleGeneration(pdfFile)
        ).getOrThrow()
    }

    @Test
    fun `test openpdf generate block quote`() = testPdf {
        val pdfFile = File("build/tmp/blockquote.pdf")
        val content = """
            > quoted line
            > second line
        """.trimIndent()
        OpenPdf().generateSignedSnapshot(
            UserInfo.EMPTY,
            UserInfo.EMPTY,
            content,
            TopicInfo.EMPTY,
            emptyMap(),
            SnapshotGeneration.SimpleGeneration(pdfFile)
        ).getOrThrow()
    }

    @Test
    fun `test openpdf generate link`() = testPdf {
        val pdfFile = File("build/tmp/link.pdf")
        val content = """
            This is a [link text](https://example.com) in paragraph.
        """.trimIndent()
        OpenPdf().generateSignedSnapshot(
            UserInfo.EMPTY,
            UserInfo.EMPTY,
            content,
            TopicInfo.EMPTY,
            emptyMap(),
            SnapshotGeneration.SimpleGeneration(pdfFile)
        ).getOrThrow()
    }
}

fun testPdf(block: () -> Unit) {
    setLogPath()
    block()
}
