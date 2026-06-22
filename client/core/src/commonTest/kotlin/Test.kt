import com.storyteller_f.a.client.core.ClientSessionState
import com.storyteller_f.a.client.core.SimplePassHolder
import com.storyteller_f.a.client.core.SingleFlightCustomAuthPlugin
import com.storyteller_f.a.client.core.UserPass
import com.storyteller_f.a.client.core.UserSessionModel
import com.storyteller_f.a.client.core.addRequestHeadersFromInfo
import com.storyteller_f.a.client.core.configClientAuth
import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.UserInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class Test {
    @Test
    fun `test request`() = Unit

    @Test
    fun `custom auth refresh is single flight for concurrent 401 responses`() = runTest {
        val initialResponsesReleased = CompletableDeferred<Unit>()
        val lock = Mutex()
        var initial401Count = 0
        val retryCookies = mutableListOf<String?>()
        val userPass = CountingUserPass(initialResponsesReleased)
        val passHolder = SimplePassHolder().also {
            it.updateState(ClientSessionState.Success(userPass))
        }
        val manager = UserSessionModel().also {
            it.updateUser(UserInfo.EMPTY)
        }
        val client = buildSingleFlightTestClient(manager, passHolder) { request ->
            handleSingleFlightRequest(request, lock, retryCookies) {
                initial401Count += 1
                if (initial401Count == 3) {
                    initialResponsesReleased.complete(Unit)
                }
            }
        }

        val responses = List(3) {
            async {
                client.get("https://example.test/resource/$it").bodyAsText()
            }
        }.awaitAll()

        assertEquals(listOf("ok", "ok", "ok"), responses)
        assertEquals(1, userPass.signatureCount)
        assertEquals(3, retryCookies.size)
        assertEquals("user_session=success", retryCookies.last())
    }

    private fun buildSingleFlightTestClient(
        manager: UserSessionModel,
        passHolder: SimplePassHolder,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ) = HttpClient(MockEngine) {
        install(SingleFlightCustomAuthPlugin) {
            configClientAuth(manager, passHolder) { u, l ->
                addRequestHeadersFromInfo(u, l)
            }
        }
        install(HttpCookies) {
            storage = AcceptAllCookiesStorage()
        }
        engine {
            addHandler(handler)
        }
    }

    private suspend fun MockRequestHandleScope.handleSingleFlightRequest(
        request: HttpRequestData,
        lock: Mutex,
        retryCookies: MutableList<String?>,
        onInitial401: () -> Unit
    ): HttpResponseData {
        val auth = request.headers[HttpHeaders.Authorization]
        return if (auth == null) {
            lock.withLock { onInitial401() }
            respond(
                content = "unauthorized",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(
                    HttpHeaders.WWWAuthenticate to listOf("Custom auth-data"),
                    HttpHeaders.SetCookie to listOf("user_session=pending; Path=/")
                )
            )
        } else {
            lock.withLock {
                retryCookies += request.headers[HttpHeaders.Cookie]
            }
            respond(
                content = "ok",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.SetCookie to listOf("user_session=success; Path=/"))
            )
        }
    }
}

private class CountingUserPass(
    private val initialResponsesReleased: CompletableDeferred<Unit>
) : UserPass {
    var signatureCount = 0
        private set

    override suspend fun signature(data: String): Result<String> {
        signatureCount += 1
        initialResponsesReleased.await()
        return Result.success("signature-$signatureCount")
    }

    override suspend fun verify(signature: String, data: String): Result<Boolean> = Result.success(true)

    override suspend fun decrypt(encrypted: ByteArray, encryptedAesKey: ByteArray): Result<String> = Result.success("")

    override suspend fun address(): Result<String> = Result.success("address")

    override suspend fun decryptChildAccount(
        encryptedPrivateKey: String,
        encryptedAesKey: String,
        childAlgoType: AlgoType,
        encryptedEncryptionPrivateKey: String?
    ): Result<Pair<String, String?>> = Result.success("" to null)

    override suspend fun encryptChildAccount(
        childAlgoType: AlgoType
    ): Result<com.storyteller_f.a.api.CustomApi.Accounts.ChildAccounts.AddChildAccountRequest> =
        Result.failure(UnsupportedOperationException())
}
