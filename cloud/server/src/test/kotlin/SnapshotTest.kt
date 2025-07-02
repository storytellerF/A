import com.storyteller_f.a.cloud.server.createKeystore
import com.storyteller_f.a.cloud.server.service.SnapshotVerify
import com.storyteller_f.a.cloud.server.service.generateSignedSnapshot
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import org.apache.pdfbox.examples.signature.ShowSignature
import org.apache.pdfbox.pdmodel.encryption.SecurityProvider
import java.io.File
import java.security.Security
import kotlin.test.Test

class SnapshotTest {
    @Test
    fun `test generate signed pdf`() {
        val password = "123456"
        val path = "build/test/keystore2.p12"
        createKeystore(password.toCharArray(), path)
        Security.addProvider(SecurityProvider.getProvider())
        val pdfFile = File("build/tmp/.pdf")
        val signedFile = File("build/tmp/signed.pdf")
        generateSignedSnapshot(
            UserInfo.EMPTY,
            UserInfo.EMPTY,
            TopicInfo.EMPTY.copy(content = TopicContent.Plain("hello world")),
            SnapshotVerify.KeyStoreVerify(path, password, pdfFile, signedFile)
        ).getOrThrow()

        ShowSignature().showSignature(signedFile, password)
    }
}
