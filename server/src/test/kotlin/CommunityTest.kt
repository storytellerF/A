import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.buildBackendFromEnv
import com.storyteller_f.naming.NameService
import com.storyteller_f.readEnv
import com.storyteller_f.shared.*
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.NewTopic
import com.storyteller_f.shared.obj.TopicSnapshotPack
import com.storyteller_f.shared.type.OKey
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.Community
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class CommunityTest {
    @Test
    fun `test create topic in community`() = test { client ->
        session(client) {
            //insert community
            val communityId = createCommunity()
            assertFails {
                val response = client.createNewTopic(ObjectType.COMMUNITY, communityId, "hello")
                assertEquals(HttpStatusCode.Forbidden, response.status)
            }
            //加入社区
            client.joinCommunity(communityId)
            val communityInfo = client.get("/community/${communityId}").body<CommunityInfo>()
            //验证加入成功
            assertTrue(communityInfo.isJoined)
            //再次发起创建话题
            kotlin.run {
                val response = client.createNewTopic(ObjectType.COMMUNITY, communityId, "hello")
                assertEquals(HttpStatusCode.OK, response.status)
            }
            //测试上传加密话题
            assertFails {
                client.post("/topic") {
                    contentType(ContentType.Application.Json)
                    setBody(NewTopic(ObjectType.COMMUNITY, communityId, TopicContent.Encrypted("", emptyMap())))
                }
            }
            //添加话题到子话题
            kotlin.run {
                val topicId = client.getCommunityTopics(communityId, 10).data.first().id
                val new = client.createNewTopic(ObjectType.TOPIC, topicId, "test").body<TopicInfo>()
                assertEquals(ObjectType.COMMUNITY, new.rootType)
                assertEquals(communityId, new.rootId)
            }
        }

    }

    @Test
    fun `test topic snapshot`() {
        test { client ->
            session(client) {
                val communityId = createCommunity()
                client.joinCommunity(communityId)
                val topicInfo = client.createNewTopic(ObjectType.COMMUNITY, communityId, "hello").body<TopicInfo>()
                val pack = client.getTopicSnapshot(topicInfo.id).body<TopicSnapshotPack>()
                assertEquals("true", client.verifySnapshot(pack).bodyAsText())
            }
        }

    }

    @Test
    fun `test communities pagination`() {
        test { client ->
            val communities = buildList {
                repeat(10) {
                    val newId = SnowflakeFactory.nextId()
                    val id = DatabaseFactory.dbQuery {
                        Community.new(Community("aid$it", "name", null, 0u, null, newId, now()))
                    }
                    add(id)
                }
            }
            session(client) {
                communities.forEach {
                    client.joinCommunity(it)
                }
                var lastCommunityId: OKey? = null
                var sum = 0L
                while (true) {
                    val res = client.getJoinCommunities(lastCommunityId, 3)
                    val pagination = res.pagination!!
                    lastCommunityId = pagination.nextPageToken?.toULong()
                    sum += res.data.size
                    if (lastCommunityId == null) {
                        assertEquals(pagination.total, sum)
                        break
                    }
                }
            }

        }
    }

    private suspend fun createCommunity(): OKey {
        val newId = SnowflakeFactory.nextId()
        return DatabaseFactory.dbQuery {
            Community.new(Community("aid", "name", null, 0u, null, newId, now()))
        }
    }

    @Test
    fun `test hmac`() {
        runBlocking {
            val backend = buildBackendFromEnv(readEnv())
            val hmacKey = backend.config.hmacKey
            val s = hmacSign(hmacKey, "text")
            assertTrue(hmacVerify(hmacKey, s, "text"))
        }
    }

    @Test
    fun `test generate hmac key`() {
        runBlocking {
            println(newHmacSha512())
        }
    }

    @Test
    fun `test name`() {
        runBlocking {
            SnowflakeFactory.setMachine(0)
            println(NameService().parse(SnowflakeFactory.nextId()))
        }
    }
}

@Suppress("unused")
fun Application.module() {
    log.info("Hello from test!")
    routing {
        get {
            call.respond(HttpStatusCode.OK, "Hello, world!")
        }
    }
}

@OptIn(ExperimentalPathApi::class)
fun test(block: suspend (HttpClient) -> Unit) {
    SnowflakeFactory.setMachine(0)
    testApplication {
        addProvider()
        val path = Paths.get("../deploy/lucene_data/index")
        val backend = buildBackendFromEnv(readEnv())
        DatabaseFactory.init(backend.config.databaseConnection)
        environment {
            config = MapApplicationConfig(
                "ktor.application.modules.0" to "CommunityTestKt.module",
                "ktor.application.modules.1" to "com.storyteller_f.a.server.ApplicationKt.module",
                "ktor.application.modules.size" to "2"
            )
        }
        val client = createClient {
            defaultClientConfigure()
        }
        block(client)
        path.deleteRecursively()
        DatabaseFactory.clean()
    }
}

suspend fun session(client: HttpClient, block: suspend () -> Unit) {
    val priKey = generateKeyPair()
    val pubKey = getDerPublicKeyFromPrivateKey(priKey)
    val address = calcAddress(pubKey)
    val data = finalData(client.getData())
    val sign = signature(priKey, data)
    val userInfo = client.sign(true, pubKey, sign, address)
    LoginViewModel.updateState(ClientSession.LoginSuccess(priKey, pubKey, address))
    LoginViewModel.updateUser(userInfo)
    block()
}