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

    fun createKeystore(keystorePassword: CharArray, path: String) {
        val alias = "snapshot"
        val validityDays = 365L

        // 注册 BouncyCastle 提供者（如未自动加载）
        Security.addProvider(BouncyCastleProvider())

        // 1. 生成密钥对
        val keyPairGen = KeyPairGenerator.getInstance("RSA", "BC")
        keyPairGen.initialize(2048, SecureRandom())
        val keyPair: KeyPair = keyPairGen.generateKeyPair()

        // 2. 证书信息
        val issuer = X500Name("CN=Example, OU=Org, O=Company, L=City, ST=State, C=US")
        val subject = issuer // 自签名
        val serial = BigInteger(160, SecureRandom())
        val notBefore = Date(System.currentTimeMillis())
        val notAfter = Date(System.currentTimeMillis() + validityDays * 24 * 60 * 60 * 1000)

        // 3. 构建 X509 证书
        val certBuilder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
            issuer,
            serial,
            notBefore,
            notAfter,
            subject,
            keyPair.public
        )

        // 4. 生成签名
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val certHolder = certBuilder.build(signer)
        val cert: X509Certificate = JcaX509CertificateConverter()
            .setProvider("BC")
            .getCertificate(certHolder)

        // 5. 创建 PKCS12 keystore 并存储证书和私钥
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry(alias, keyPair.private, keystorePassword, arrayOf(cert))

        // 6. 保存 keystore 到文件
        FileOutputStream(path).use { fos ->
            ks.store(fos, keystorePassword)
        }
    }
}
