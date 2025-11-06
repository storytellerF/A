package com.storyteller_f.a.cloud.server

import com.storyteller_f.a.backend.core.setLogPath
import com.storyteller_f.a.cloud.openpdf.OpenPdf
import com.storyteller_f.a.cloud.pdf.PdfGenerationSpec
import com.storyteller_f.a.cloud.pdf.SnapshotGeneration
import com.storyteller_f.a.cloud.pdfbox.PdfBox
import com.storyteller_f.shared.CryptoJvm
import com.storyteller_f.shared.model.UserInfo
import de.redsix.pdfcompare.PdfComparator
import kotlinx.datetime.LocalDateTime
import org.apache.pdfbox.pdmodel.encryption.SecurityProvider
import org.intellij.lang.annotations.Language
import java.io.File
import java.security.Security
import kotlin.test.Test
import kotlin.test.assertTrue

class SnapshotTest {

    @Test
    fun `test generate signed pdf`() {
        val password1 = "123456"
        val path1 = "build/test/keystore2.p12"
        CryptoJvm.createKeystore(password1.toCharArray(), path1)
        Security.addProvider(SecurityProvider.getProvider())
        listOf(OpenPdf(), PdfBox()).forEachIndexed { i, pdf ->
            val pdfFile = File("build/tmp/$i.pdf")
            val signedFile = File("build/tmp/$i.signed.pdf")
            pdf.generateSignedSnapshot(
                UserInfo.EMPTY,
                UserInfo.EMPTY,
                "hello world",
                emptyMap(),
                SnapshotGeneration.KeyStoreGeneration(path1, password1, pdfFile, signedFile),
                PdfGenerationSpec(
                    LocalDateTime.parse("2023-01-01T00:00:00"),
                    LocalDateTime.parse("2023-01-01T00:00:00")
                )
            ).getOrThrow()
        }
    }

    @Test
    fun `test openpdf generate code fence`() = openPdfSnapshot(
        @Language("kotlin") """```kotlin
                |fun main() {
                |    println("hello world")
                |}
                |```""".trimMargin()
    )

    @Test
    fun `test openpdf generate headings`() = openPdfSnapshot(
        """
        # Heading 1
        ## Heading 2
        Normal paragraph

        Heading A
        ===

        Heading B
        ---
        """.trimIndent()
    )

    @Test
    fun `test openpdf generate emphasis and strong`() = openPdfSnapshot(
        """
        *italic* and **bold** text with normal content.
        """.trimIndent()
    )

    @Test
    fun `test openpdf generate code span`() = openPdfSnapshot(
        """
        Inline `code` span inside a sentence.
        """.trimIndent()
    )

    @Test
    fun `test openpdf generate lists`() = openPdfSnapshot(
        """
        - item 1
            - nested item 1.1
        - item 2

        1. first
        2. second
            1. sub first
        """.trimIndent()
    )

    @Test
    fun `test openpdf generate block quote`() = openPdfSnapshot(
        """
        > quoted line
        > second line
        """.trimIndent()
    )

    @Test
    fun `test openpdf generate link`() = openPdfSnapshot(
        """
        This is a [link text](https://example.com) in paragraph.
        """.trimIndent()
    )
}

private fun openPdfSnapshot(content: String, map: Map<String, File> = emptyMap()) {
    setLogPath()

    // 从异常堆栈获取当前测试函数名
    val methodName = Exception().stackTrace.first {
        it.className.endsWith("SnapshotTest")
    }.methodName
    val snapshotDir = File("src/test/pdf-snapshot").apply { mkdirs() }
    val snapshotFile = File(snapshotDir, "$methodName.pdf")

    val actualFile = if (snapshotFile.exists()) {
        File("build/tmp/$methodName.actual.pdf")
    } else {
        snapshotFile
    }

    OpenPdf().generateSignedSnapshot(
        UserInfo.EMPTY,
        UserInfo.EMPTY,
        content,
        map,
        SnapshotGeneration.SimpleGeneration(actualFile),
        PdfGenerationSpec(
            LocalDateTime.parse("2023-01-01T00:00:00"),
            LocalDateTime.parse("2023-01-01T00:00:00")
        )
    ).getOrThrow()

    if (snapshotFile.exists()) {
        // 支持通过环境变量刷新快照：UPDATE_SNAPSHOTS=1 覆盖现有快照
        val updateSnapshots = System.getenv("UPDATE_SNAPSHOTS") == "1"
        if (updateSnapshots) {
            // 用本次生成的 actual 覆盖 baseline
            actualFile.copyTo(snapshotFile, overwrite = true)
            return
        }
        val result = PdfComparator<de.redsix.pdfcompare.CompareResultImpl>(
            snapshotFile.absolutePath,
            actualFile.absolutePath
        ).compare()
        // 可选：输出 diff 到目录（返回是否相等）
        result.writeTo("build/tmp/$methodName-diff")
        assertTrue(result.isEqual(), "PDF snapshot mismatch for $methodName")
    }
}
