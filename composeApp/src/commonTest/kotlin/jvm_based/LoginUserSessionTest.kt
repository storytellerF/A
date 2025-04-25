package jvm_based

import com.storyteller_f.a.app.utils.buildLoginUserSessionFactory
import com.storyteller_f.a.client_lib.LoginUser
import com.storyteller_f.shared.calcAddress
import com.storyteller_f.shared.eciesEncrypt
import com.storyteller_f.shared.encryptData
import com.storyteller_f.shared.generateECDSAPemPrivateKey
import com.storyteller_f.shared.getDerPublicKeyFromPrivateKey
import com.storyteller_f.shared.loadIfNeed
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoginUserSessionTest : UsingContextTest() {
    @Test
    fun testSession() = runTest {
        loadIfNeed()
        val sessionFactory = buildLoginUserSessionFactory()
        assertEquals(0, sessionFactory.savedSession().list.size)
        val privateKey = generateECDSAPemPrivateKey()
        val publicKey = getDerPublicKeyFromPrivateKey(privateKey)
        val ad = calcAddress(publicKey)
        val addSession = sessionFactory.addSession(LoginUser(privateKey, publicKey, ad))
        assertEquals(1, sessionFactory.savedSession().list.size)
        val signature = addSession.signature("test")
        assertTrue(addSession.verify(signature, "test"))
        val encrypt = encryptData("hello")
        val encryptAesKey = eciesEncrypt(publicKey, encrypt.second)
        assertEquals("hello", addSession.decrypt(encrypt.first, encryptAesKey))
        sessionFactory.buildSession("default")
        sessionFactory.removeSession("default")
        assertEquals(0, sessionFactory.savedSession().list.size)
    }
}