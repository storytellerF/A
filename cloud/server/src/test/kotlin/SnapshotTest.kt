import com.storyteller_f.a.server.service.SnapshotVerify
import com.storyteller_f.a.server.service.generateSignedSnapshot
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import org.apache.pdfbox.examples.signature.ShowSignature
import org.apache.pdfbox.pdmodel.encryption.SecurityProvider
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.*
import java.security.cert.X509Certificate
import java.util.*
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
