import com.storyteller_f.shared.*
import com.storyteller_f.shared.AlgoP256
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import kotlinx.coroutines.test.runTest
import org.bouncycastle.pqc.jcajce.spec.DilithiumParameterSpec
import org.bouncycastle.pqc.jcajce.spec.KyberParameterSpec
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals

class SignatureTest {
    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun `test encrypt`() {
        runTest {
            getAlgo().run {
                val (encrypted, aesKey) = encryptDataByAES("hello").getOrThrow()
                assertEquals("hello", decryptDataByAES(encrypted, aesKey).getOrThrow())
                val keyPair = CryptographyProvider.Default.get(ECDSA).keyPairGenerator(EC.Curve.P256).generateKey()
                val derPublicKey = keyPair.publicKey.encodeToByteArray(EC.PublicKey.Format.DER)
                val derPrivateKey = keyPair.privateKey.encodeToByteArray(EC.PrivateKey.Format.DER)
                val encrypt = eciesEncrypt(derPublicKey.toHexString(), "hello".encodeToByteArray()).getOrThrow()
                val decrypted = eciesDecrypt(derPrivateKey.toHexString(), encrypt).getOrThrow()
                assertEquals("hello", decrypted.decodeToString())
            }
        }
    }

    @Test
    fun `test address`() {
        loadCryptoLibIfNeed()
        runTest {
            getAlgo().run {
                val keyPair = generateECDSAPemPrivateKey().getOrThrow()
                val key = getDerPublicKeyFromPrivateKey(keyPair).getOrThrow()
                println(AlgoP256.calcAddress(key).getOrThrow())
            }
        }

    }


    @Test
    fun `test pqc`() {
        loadCryptoLibIfNeed()
        // 1) 生成密钥对
        val kpg = KeyPairGenerator.getInstance("Dilithium", "BCPQC")
        kpg.initialize(DilithiumParameterSpec.dilithium3, SecureRandom()) // 选择参数集：dilithium2/3/5 等
        val kp: KeyPair = kpg.generateKeyPair()


        // 2) 签名
        val signer: Signature = Signature.getInstance("Dilithium", "BCPQC")
        signer.initSign(kp.private, SecureRandom())
        val message = "hello post-quantum".toByteArray()
        signer.update(message)
        val signature: ByteArray? = signer.sign()


        // 3) 验证
        val verifier: Signature = Signature.getInstance("Dilithium", "BCPQC")
        verifier.initVerify(kp.public)
        verifier.update(message)
        val ok: Boolean = verifier.verify(signature)
        println("verify: $ok")
    }

    @Test
    fun `test kyber`() {
        loadCryptoLibIfNeed()
        // 选择 Kyber 参数集
        val kyberSpec = KyberParameterSpec.kyber768

        // 生成 Kyber 密钥对（Alice）
        val keyGen = KeyPairGenerator.getInstance("Kyber", "BCPQC")
        keyGen.initialize(kyberSpec)
        val aliceKP = keyGen.generateKeyPair()

        // Bob 用 Alice 公钥封装 AES 共享密钥
        val sharedSecret = ByteArray(32) { 0x01 } // 模拟要传递的 256-bit 密钥
        val bobAESKey = SecretKeySpec(sharedSecret, "AES")

        val wrapCipher = Cipher.getInstance("Kyber", "BCPQC")
        wrapCipher.init(Cipher.WRAP_MODE, aliceKP.public)
        val ciphertext = wrapCipher.wrap(bobAESKey) // 生成封装密文

        println("Ciphertext length: ${ciphertext.size} bytes")

        // Alice 用私钥解封
        val unwrapCipher = Cipher.getInstance("Kyber", "BCPQC")
        unwrapCipher.init(Cipher.UNWRAP_MODE, aliceKP.private)
        val aliceKey = unwrapCipher.unwrap(ciphertext, "AES", Cipher.SECRET_KEY) as SecretKey

        // 验证密钥一致
        val match = aliceKey.encoded.contentEquals(sharedSecret)
        println("密钥协商成功: $match")
    }
}