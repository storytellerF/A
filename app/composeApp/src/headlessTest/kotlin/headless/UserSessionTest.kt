package headless

import PlatformHeadlessTest
import com.storyteller_f.a.app.core.utils.SessionHistoryManager
import com.storyteller_f.a.app.core.utils.buildSessionHistoryFactory
import com.storyteller_f.a.app.core.utils.createSettings
import com.storyteller_f.a.client.core.AuthKey
import com.storyteller_f.a.client.core.RawUserPassInfo
import com.storyteller_f.shared.encryptDataByAES
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.loadCryptoLibIfNeed
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 存在错误AndroidContextProvider ProviderInfo cannot be null.
 * 全部禁用
 */
class LoginUserSessionTest : PlatformHeadlessTest() {
    @Test
    fun testSession() = loginSessionTest { ad, sessionFactory, authKey ->
        val addSession = sessionFactory.addSession(RawUserPassInfo(ad, authKey))
        assertEquals(1, sessionFactory.getSavedSession().alias.size)
        // 签名/验证
        val signature = addSession.signature("test").getOrThrow()
        assertTrue(addSession.verify(signature, "test").getOrThrow())
        // 加密测试
        val encrypt = encryptDataByAES("hello").getOrThrow()
        getAlgo().run {
            val encryptAesKey = encryptionAlgo.kemEncrypt(authKey.derPublicKey, encrypt.second).getOrThrow()
            assertEquals("hello", addSession.decrypt(encrypt.first, encryptAesKey).getOrThrow())
        }
    }

    @Test
    fun `test exit session`() = loginSessionTest { ad, sessionFactory, authKey ->
        sessionFactory.addSession(RawUserPassInfo(ad, authKey))
        val session = sessionFactory.getSavedSession()
        assertEquals(ad, session.history?.current)
        assertEquals(ad, session.history?.last)
        sessionFactory.exitSession(ad)
        val session1 = sessionFactory.getSavedSession()
        assertNull(session1.history?.current)
        assertEquals(ad, session1.history?.last)
    }

    @Test
    fun `test remove session`() = loginSessionTest { ad, sessionFactory, authKey ->
        sessionFactory.addSession(RawUserPassInfo(ad, authKey))
        val session = sessionFactory.getSavedSession()
        assertEquals(ad, session.history?.current)
        assertEquals(ad, session.history?.last)
        sessionFactory.removeSession(ad)
        val session1 = sessionFactory.getSavedSession()
        assertNull(session1.history?.current)
        assertNull(session1.history?.last)
        assertEquals(0, session1.alias.size)
    }

    @Test
    fun `test build session`() = loginSessionTest { ad, sessionFactory, authKey ->
        sessionFactory.addSession(RawUserPassInfo(ad, authKey))
        val session = sessionFactory.getSavedSession()
        val alias = session.history?.last
        assertNotNull(alias)
        val userPass = sessionFactory.buildSession(alias)
        assertEquals(ad, userPass?.address()?.getOrThrow())
    }
}

fun loginSessionTest(block: suspend (String, SessionHistoryManager, AuthKey) -> Unit) =
    runTest {
        loadCryptoLibIfNeed()
        val settings = createSettings("settings-test")
        settings.clear()
        val sessionFactory = buildSessionHistoryFactory(settings)
        assertEquals(0, sessionFactory.getSavedSession().alias.size)
        getAlgo().run {
            val privateKey = generatePemKeyPair().getOrThrow().first
            val derPrivateKey = getDerPrivateKey(privateKey).getOrThrow()
            val publicKey = getDerPublicKeyFromPrivateKey(privateKey).getOrThrow()
            val ad = calcAddress(publicKey).getOrThrow()
            val authKey = AuthKey.P256(privateKey, derPrivateKey, publicKey)
            block(ad, sessionFactory, authKey)
        }
    }
