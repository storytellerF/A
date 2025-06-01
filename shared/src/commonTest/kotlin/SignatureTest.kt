import com.storyteller_f.shared.*
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SignatureTest {
    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun `test encrypt`() {
        runTest {
            val (encrypted, aesKey) = encryptData("hello").getOrThrow()
            assertEquals("hello", decryptData(encrypted, aesKey).getOrThrow())
            val keyPair = CryptographyProvider.Default.get(ECDSA).keyPairGenerator(EC.Curve.P256).generateKey()
            val derPublicKey = keyPair.publicKey.encodeToByteArray(EC.PublicKey.Format.DER)
            val derPrivateKey = keyPair.privateKey.encodeToByteArray(EC.PrivateKey.Format.DER)
            val encrypt = eciesEncrypt(derPublicKey.toHexString(), "hello".encodeToByteArray()).getOrThrow()
            val decrypted = eciesDecrypt(derPrivateKey.toHexString(), encrypt).getOrThrow()
            assertEquals("hello", decrypted.decodeToString())
        }
    }

    @Test
    fun `test address`() {
        loadCryptoLibIfNeed()
        runTest {
            val keyPair = generateECDSAPemPrivateKey().getOrThrow()
            val key = getDerPublicKeyFromPrivateKey(keyPair).getOrThrow()
            println(calcAddress(key).getOrThrow())
        }

    }
}