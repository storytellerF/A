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
}

fun testPdf(block: () -> Unit) {
    setLogPath()
    block()
}
