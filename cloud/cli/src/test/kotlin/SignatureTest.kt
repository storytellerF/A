import com.storyteller_f.shared.*
import com.storyteller_f.shared.obj.PresetValue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignatureTest {
    @Test
    fun test() {
        val jsonFilePath = "../../AData/data/0_pre_set_user.json"
        val jsonFile = File(jsonFilePath)
        if (!jsonFile.exists()) return
        loadCryptoLibIfNeed()

        val presetValue = Json {
            ignoreUnknownKeys = true
        }.decodeFromString<PresetValue>(jsonFile.readText())
        runTest {
            val data = "hello"
            presetValue.userData!!.forEach {
                val privateKeyStr =
                    File(jsonFile.parentFile, it.privateKey).readText().replace("\r\n", "\n")
                getAlgo().run {
                    val s = signature(privateKeyStr, data).getOrThrow()
                    val derPublicKeyStr = getDerPublicKeyFromPrivateKey(privateKeyStr).getOrThrow()
                    assertTrue(verify(derPublicKeyStr, s, data).getOrThrow(), "check failed")
                }
            }
        }
    }

    @Test
    fun testEncrypt() {
        val jsonFilePath = "../../AData/data/preset_user.json"
        val jsonFile = File(jsonFilePath)
        if (!jsonFile.exists()) return
        loadCryptoLibIfNeed()

        val presetValue = Json {
            ignoreUnknownKeys = true
        }.decodeFromString<PresetValue>(jsonFile.readText())
        runTest {
            val data = "hello"
            presetValue.userData!!.forEach {
                val privateKeyStr =
                    File(jsonFile.parentFile, it.privateKey).readText().replace("\r\n", "\n")
                getAlgo().run {
                    val derPublicKeyStr = getDerPublicKeyFromPrivateKey(privateKeyStr).getOrThrow()
                    val (encrypted, aes) = encryptDataByAES(data).getOrThrow()
                    val encryptedAes = eciesEncrypt(derPublicKeyStr, aes).getOrThrow()
                    val decrypted =
                        decryptMessage(
                            getDerPrivateKey(privateKeyStr).getOrThrow(),
                            encrypted,
                            encryptedAes
                        ).getOrThrow()
                    assertEquals(data, decrypted)

                    // 确保手动实现的算法和bc 提供的一致
                    val (encrypted1, aes1) = CryptoJvm.encrypt(data).getOrThrow()
                    assertContentEquals(encrypted, encrypted1)
                    assertContentEquals(aes, aes1)
                    val encryptedAes1 = CryptoJvm.encryptAesKey(derPublicKeyStr, aes1).getOrThrow()
                    assertContentEquals(encryptedAes, encryptedAes1)
                    val decrypted1 = CryptoJvm.decrypt(
                        getDerPrivateKey(privateKeyStr).getOrThrow(),
                        encrypted1,
                        encryptedAes1
                    ).getOrThrow()
                    assertEquals(decrypted, decrypted1)
                }
            }
        }
    }
}
