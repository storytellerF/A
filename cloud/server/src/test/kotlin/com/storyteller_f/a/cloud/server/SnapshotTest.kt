package com.storyteller_f.a.cloud.server

import com.storyteller_f.a.cloud.openpdf.OpenPdf
import com.storyteller_f.a.cloud.pdf.SnapshotVerify
import com.storyteller_f.a.cloud.pdfbox.PdfBox
import com.storyteller_f.shared.CryptoJvm
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.examples.signature.ShowSignature
import org.apache.pdfbox.pdmodel.encryption.SecurityProvider
import java.io.File
import java.security.Security
import kotlin.test.Test
import kotlin.test.assertFails

class SnapshotTest {
    @Test
    fun `test pdfbox generate signed pdf`() {
        runTest {
            val password = "123456"
            val path = "build/test/keystore2.p12"
            CryptoJvm.createKeystore(password.toCharArray(), path)
            Security.addProvider(SecurityProvider.getProvider())
            val pdfFile = File("build/tmp/.pdf")
            val signedFile = File("build/tmp/signed.pdf")
            PdfBox().generateSignedSnapshot(
                UserInfo.EMPTY,
                UserInfo.EMPTY,
                "hello world",
                TopicInfo.EMPTY.copy(content = TopicContent.Plain("hello world")),
                emptyMap(),
                //            SnapshotVerify.KeyStoreVerify(path, password, pdfFile, signedFile)
                SnapshotVerify.NoneVerify(pdfFile)
            ).getOrThrow()

            assertFails {
                ShowSignature().showSignature(signedFile, password)
            }
        }
    }

    @Test
    fun `test openpdf generate signed pdf`() {
        runTest {
            val password = "123456"
            val path = "build/test/keystore2.p12"
            CryptoJvm.createKeystore(password.toCharArray(), path)
            Security.addProvider(SecurityProvider.getProvider())
            val pdfFile = File("build/tmp/.pdf")
            OpenPdf().generateSignedSnapshot(
                UserInfo.EMPTY,
                UserInfo.EMPTY,
                "hello world",
                TopicInfo.EMPTY.copy(content = TopicContent.Plain("hello world")),
                emptyMap(),
                //            SnapshotVerify.KeyStoreVerify(path, password, pdfFile, signedFile)
                SnapshotVerify.NoneVerify(pdfFile)
            ).getOrThrow()
        }
    }
}
