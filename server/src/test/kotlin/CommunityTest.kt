import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.buildBackendFromEnv
import com.storyteller_f.readEnv
import com.storyteller_f.shared.hmacSign
import com.storyteller_f.shared.hmacVerify
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.newHmacSha512
import com.storyteller_f.shared.obj.NewTopic
import com.storyteller_f.shared.type.DEFAULT_PRIMARY_KEY
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKeyOrNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.Community
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class CommunityTest {
    @Test
    fun `test get community`() = test { client ->
        val communityId = createCommunity()
        val community = client.getCommunityInfo(communityId)
        assertEquals(communityId, client.getCommunityInfoByAid(community.aid).id)
    }

    @Test
    fun `test create topic in community`() = test { client ->
        session(client) {
            // insert community
            val communityId = createCommunity()
            assertFails {
                val response = client.createNewTopic(ObjectType.COMMUNITY, communityId, "hello")
                assertEquals(HttpStatusCode.Forbidden, response.status)
            }
            // 加入社区
            client.joinCommunity(communityId)
            val communityInfo = client.get("/community/$communityId").body<CommunityInfo>()
            // 验证加入成功
            assertTrue(communityInfo.isJoined)
            // 再次发起创建话题
            kotlin.run {
                val response = client.createNewTopic(ObjectType.COMMUNITY, communityId, "hello")
                assertEquals(HttpStatusCode.OK, response.status)
            }
            // 测试上传加密话题
            assertFails {
                client.post("/topic") {
                    contentType(ContentType.Application.Json)
                    setBody(NewTopic(ObjectType.COMMUNITY, communityId, TopicContent.Encrypted("", emptyMap())))
                }
            }
            // 添加话题到子话题
            kotlin.run {
                val topicId = client.getCommunityTopics(communityId, null, 10).data.first().id
                val new = client.createNewTopic(ObjectType.TOPIC, topicId, "test").body<TopicInfo>()
                assertEquals(ObjectType.COMMUNITY, new.rootType)
                assertEquals(communityId, new.rootId)
            }
        }
    }

    @Test
    fun `test communities pagination`() {
        test { client ->
            val communities = buildList {
                repeat(10) {
                    val newId = SnowflakeFactory.nextId()
                    DatabaseFactory.dbQuery {
                        Community.new(Community("aid$it", "name", null, DEFAULT_PRIMARY_KEY, null, newId, now()))
                    }.getOrThrow().let(::add)
                }
            }
            session(client) {
                communities.forEach {
                    client.joinCommunity(it)
                }
                var lastCommunityId: PrimaryKey? = null
                var sum = 0L
                while (true) {
                    val res = client.getJoinCommunities(lastCommunityId, 3)
                    val pagination = res.pagination!!
                    lastCommunityId = pagination.nextPageToken?.toPrimaryKeyOrNull()
                    sum += res.data.size
                    if (lastCommunityId == null) {
                        assertEquals(pagination.total, sum)
                        break
                    }
                }
            }
        }
    }

    private suspend fun createCommunity(): PrimaryKey {
        val newId = SnowflakeFactory.nextId()
        return DatabaseFactory.dbQuery {
            Community.new(Community("aid", "name", null, DEFAULT_PRIMARY_KEY, null, newId, now()))
        }.getOrThrow()
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
}
