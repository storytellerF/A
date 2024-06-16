import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.Backend
import com.storyteller_f.Config
import com.storyteller_f.DatabaseConnection
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.a.server.backend
import com.storyteller_f.a.server.service.toUserInfo
import com.storyteller_f.index.LuceneTopicDocumentService
import com.storyteller_f.media.FileSystemMediaService
import com.storyteller_f.naming.NameService
import com.storyteller_f.shared.*
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.NewTopic
import com.storyteller_f.shared.obj.TopicSnapshotPack
import com.storyteller_f.shared.type.OKey
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.*
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
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommunityTest {
    @Test
    fun `test mdbook`() {
        File("C:\\Users\\Administrator\\Projects\\A\\spec").list()?.forEach {
            val name = it.split(".").first()
            println("- [$name](./$name.md)")
        }
    }
    @Test
    fun `test create topic in community`() = test { client ->
        session {
            //insert community
            val communityId = createCommunity()
            kotlin.run {
                val response = client.createNewTopic(ObjectType.COMMUNITY, communityId, "hello")
                assertEquals(HttpStatusCode.Forbidden, response.status)
                assertEquals("未加入此社区", response.bodyAsText())
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
            kotlin.run {
                val response = client.post("/topic") {
                    contentType(ContentType.Application.Json)
                    setBody(NewTopic(ObjectType.COMMUNITY, communityId, TopicContent.Encrypted("", emptyMap())))
                }
                assertEquals(HttpStatusCode.Forbidden, response.status)
            }
            //添加话题到子话题
            kotlin.run {
                val topicId = client.getCommunityTopics(communityId).data.first().id
                val new = client.createNewTopic(ObjectType.TOPIC, topicId, "test").body<TopicInfo>()
                assertEquals(ObjectType.COMMUNITY, new.rootType)
                assertEquals(communityId, new.rootId)
            }
        }

    }

    @Test
    fun `test topic snapshot`() {
        runBlocking {
            test { client ->
                session {
                    val communityId = createCommunity()
                    client.joinCommunity(communityId)
                    val topicInfo = client.createNewTopic(ObjectType.COMMUNITY, communityId, "hello").body<TopicInfo>()
                    val pack = client.getTopicSnapshot(topicInfo.id).body<TopicSnapshotPack>()
                    assertEquals("true", client.verifySnapshot(pack).bodyAsText())
                }
            }

        }
    }

    private suspend fun createCommunity(): OKey {
        val newId = SnowflakeFactory.nextId()
        val communityId = DatabaseFactory.dbQuery {
            Community.new(Community("11", "test", null, 0u, null, newId, now()))
        }
        return communityId
    }

    @Test
    fun `test hmac`() {
        runBlocking {
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
        val path = Paths.get("./test-data/index-test")
        backend = Backend(
            Config(
                DatabaseConnection("jdbc:h2:mem:regular;DB_CLOSE_DELAY=-1;", "org.h2.Driver", "", ""),
                null,
                newHmacSha512()
            ),
            LuceneTopicDocumentService(path),
            FileSystemMediaService(),
            NameService()
        )
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

suspend fun session(block: suspend () -> Unit) {
    val priKey = generateKeyPair()
    val pubKey = getDerPublicKeyFromPrivateKey(priKey)
    val add = calcAddress(pubKey)
    val id = SnowflakeFactory.nextId()
    val userInfo = DatabaseFactory.query(User::toUserInfo) {
        createUser(User(null, pubKey, add, null, "test", id, now()))
    }
    LoginViewModel.updateState(ClientSession.LoginSuccess(priKey, pubKey, add))
    LoginViewModel.updateUser(userInfo)
    block()
}