import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.storyteller_f.shared.*
import com.storyteller_f.shared.eciesEncrypt
import com.storyteller_f.shared.obj.PresetValue
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignatureTest {
    @Test
    fun test() {
        val jsonFilePath = "../../AData/data/0_pre_set_user.json"
        val jsonFile = File(jsonFilePath)
        if (!jsonFile.exists()) return
        loadIfNeed()

        val presetValue =
            ObjectMapper().registerModule(KotlinModule.Builder().build())
                .readValue<PresetValue>(jsonFile.readText())
        runTest {
            val data = "hello"
            presetValue.userData!!.forEach {
                val privateKeyStr = File(jsonFile.parentFile, it.privateKey).readText().replace("\r\n", "\n")
                val s = signature(privateKeyStr, data)
                val derPublicKeyStr = getDerPublicKeyFromPrivateKey(privateKeyStr)
                assertTrue(verify(derPublicKeyStr, s, data), "check failed")
            }
        }
    }

    @Test
    fun testEncrypt() {
        val jsonFilePath = "../../AData/data/preset_user.json"
        val jsonFile = File(jsonFilePath)
        if (!jsonFile.exists()) return
        loadIfNeed()

        val presetValue =
            ObjectMapper().registerModule(KotlinModule.Builder().build())
                .readValue<PresetValue>(jsonFile.readText())
        runTest {
            val data = "hello"
            presetValue.userData!!.forEach {
                val privateKeyStr = File(jsonFile.parentFile, it.privateKey).readText().replace("\r\n", "\n")
                val derPublicKeyStr = getDerPublicKeyFromPrivateKey(privateKeyStr)
                val (encrypted, aes) = encryptData(data)
                val encryptedAes = eciesEncrypt(derPublicKeyStr, aes)
                val decrypted = decryptMessage(getDerPrivateKey(privateKeyStr), encrypted, encryptedAes)
                assertEquals(data, decrypted)
            }
        }
    }
}
