package jvm_based

import com.storyteller_f.a.app.utils.buildLoginUserSessionFactory
import com.storyteller_f.a.client_lib.LoginUser
import com.storyteller_f.shared.calcAddress
import com.storyteller_f.shared.encrypt
import com.storyteller_f.shared.encryptAesKey
import com.storyteller_f.shared.generateKeyPair
import com.storyteller_f.shared.getDerPublicKeyFromPrivateKey
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import com.storyteller_f.crypto_jvm.addProviderForJvm
import kotlin.test.assertTrue

class LoginUserSessionTest : UsingContextTest() {
    @Test
    fun testSession() = runTest {
        addProviderForJvm()
        val sessionFactory = buildLoginUserSessionFactory()
        assertEquals(0, sessionFactory.savedSession().list.size)
        val privateKey = generateKeyPair()
        val publicKey = getDerPublicKeyFromPrivateKey(privateKey)
        val ad = calcAddress(publicKey)
        val addSession = sessionFactory.addSession(LoginUser(privateKey, publicKey, ad))
        assertEquals(1, sessionFactory.savedSession().list.size)
        val signature = addSession.signature("test")
        assertTrue(addSession.verify(signature, "test"))
        val encrypt = encrypt("hello")
        val encryptAesKey = encryptAesKey(publicKey, encrypt.second)
        assertEquals("hello", addSession.decrypt(encrypt.first, encryptAesKey))
        sessionFactory.buildSession("default")
        sessionFactory.removeSession("default")
        assertEquals(0, sessionFactory.savedSession().list.size)
    }
}