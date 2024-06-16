import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.storyteller_f.shared.*
import com.storyteller_f.shared.obj.AddTaskValue
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignatureTest {
    @Test
    fun test() {
        val jsonFilePath = "../../AData/data/preset_user.json"
        val jsonFile = File(jsonFilePath)
        if (!jsonFile.exists()) return
        addProvider()

        val addTaskValue =
            ObjectMapper().registerModule(KotlinModule.Builder().build())
                .readValue<AddTaskValue>(jsonFile.readText())
        runBlocking {
            val data = "hello"
            addTaskValue.userData!!.forEach {
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
        addProvider()

        val addTaskValue =
            ObjectMapper().registerModule(KotlinModule.Builder().build())
                .readValue<AddTaskValue>(jsonFile.readText())
        runBlocking {
            val data = "hello"
            addTaskValue.userData!!.forEach {
                val privateKeyStr = File(jsonFile.parentFile, it.privateKey).readText().replace("\r\n", "\n")
                val derPublicKeyStr = getDerPublicKeyFromPrivateKey(privateKeyStr)
                val (encrypted, aes) = encrypt(data)
                val encryptedAes = encryptAesKey(derPublicKeyStr, aes)
                val decrypted = decrypt(getDerPrivateKey(privateKeyStr), encrypted, encryptedAes)
                assertEquals(data, decrypted)
            }

        }
    }
}