import com.storyteller_f.a.app.utils.buildLoginUserSessionFactory
import com.storyteller_f.a.app.utils.createSettings
import com.storyteller_f.a.client.core.RawUserPassInfo
import com.storyteller_f.shared.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoginUserSessionTest : UsingContextTest() {
    @Test
    fun testSession() = runTest {
        loadCryptoLibIfNeed()
        val settings = createSettings("settings-test")
        settings.clear()
        val sessionFactory = buildLoginUserSessionFactory(settings)
        assertEquals(0, sessionFactory.savedSession().list.size)
        val privateKey = generateECDSAPemPrivateKey().getOrThrow()
        val publicKey = getDerPublicKeyFromPrivateKey(privateKey).getOrThrow()
        val ad = calcAddress(publicKey).getOrThrow()
        val addSession = sessionFactory.addSession(RawUserPassInfo(privateKey, publicKey, ad))
        assertEquals(1, sessionFactory.savedSession().list.size)
        val signature = addSession.signature("test").getOrThrow()
        assertTrue(addSession.verify(signature, "test").getOrThrow())
        val encrypt = encryptData("hello").getOrThrow()
        val encryptAesKey = eciesEncrypt(publicKey, encrypt.second).getOrThrow()
        assertEquals("hello", addSession.decrypt(encrypt.first, encryptAesKey).getOrThrow())
        sessionFactory.buildSession("default")
        sessionFactory.removeSession("default")
        assertEquals(0, sessionFactory.savedSession().list.size)
    }
}