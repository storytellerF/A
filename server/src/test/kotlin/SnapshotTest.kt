import com.storyteller_f.a.server.service.generateSignedSnapshot
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

        // register BouncyCastle provider, needed for "exotic" algorithms
        Security.addProvider(SecurityProvider.getProvider())
        val pdfFile = File("build/tmp/2.pdf")
        val signedFile = File("build/tmp/2_signed.pdf")
        generateSignedSnapshot(UserInfo.EMPTY, UserInfo.EMPTY, TopicInfo.EMPTY, pdfFile, signedFile, "hello").getOrThrow()

        ShowSignature().showSignature(signedFile, "123456")
    }
}