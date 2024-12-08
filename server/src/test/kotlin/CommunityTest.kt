import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.hmacSign
import com.storyteller_f.shared.hmacVerify
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.newHmacSha512
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.obj.NewTopic
import com.storyteller_f.shared.type.DEFAULT_PRIMARY_KEY
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKeyOrNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.Community
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class CommunityTest {
    @Test
    fun `test get community`() = test { client ->
        val newId = SnowflakeFactory.nextId()
        val communityId = DatabaseFactory.dbQuery {
            Community.new(Community("aid", "name", null, DEFAULT_PRIMARY_KEY, null, newId, now()))
        }.getOrThrow()
        val community = client.getCommunityInfo(communityId)
        assertEquals(communityId, client.getCommunityInfoByAid(community.aid).id)
    }

    @Test
    fun `test create topic in community`() = test { client ->
        attachSession(client) {
            // insert community
            val newId = SnowflakeFactory.nextId()
            val communityId = DatabaseFactory.dbQuery {
                Community.new(Community("aid", "name", null, DEFAULT_PRIMARY_KEY, null, newId, now()))
            }.getOrThrow()
            assertFails {
                val response = client.createNewTopic(ObjectType.COMMUNITY, communityId, "hello")
                assertEquals(HttpStatusCode.Forbidden, response.status)
            }
            // 加入社区
            client.joinCommunity(communityId)
            val communityInfo = client.getCommunityInfo(communityId, true)
            // 验证加入成功
            assertTrue(communityInfo.isJoined)
            // 再次发起创建话题
            kotlin.run {
                val response = client.createNewTopic(ObjectType.COMMUNITY, communityId, "hello")
                assertEquals(HttpStatusCode.OK, response.status)
            }
            // 测试上传加密话题
            assertFails {
                client.post("/topics") {
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
                val newInfo = client.getTopicInfo(topicId)
                assertTrue(newInfo.hasComment)
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
            attachSession(client) {
                communities.forEach {
                    client.joinCommunity(it)
                }
                var lastCommunityId: PrimaryKey? = null
                var sum = 0L
                while (true) {
                    val res = client.searchCommunity(lastCommunityId, 3, JoinStatusSearch.JOINED, "")
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

    @Test
    fun `test hmac`() {
        runBlocking {
            val hmacKey = newHmacSha512()
            val s = hmacSign(hmacKey, "text")
            assertTrue(hmacVerify(hmacKey, s, "text"))
        }
    }

    @Test
    fun `test search community`() {
        test { client ->
            val community1 = SnowflakeFactory.nextId()
            DatabaseFactory.dbQuery {
                Community.new(Community("c1", "name1", null, DEFAULT_PRIMARY_KEY, null, community1, now()))
            }.getOrThrow()
            val community2 = SnowflakeFactory.nextId()
            DatabaseFactory.dbQuery {
                Community.new(Community("c2", "name2", null, DEFAULT_PRIMARY_KEY, null, community2, now()))
            }.getOrThrow()
            attachSession(client) {
                client.joinCommunity(community1)
                assertEquals(1, client.searchCommunity(null, 10, JoinStatusSearch.JOINED, null).data.size)
                assertEquals(1, client.searchCommunity(null, 10, JoinStatusSearch.NOT_JOINED, null).data.size)
                assertEquals(2, client.searchCommunity(null, 10, JoinStatusSearch.UNSPECIFIED, null).data.size)
                assertEquals(1, client.searchCommunity(null, 10, JoinStatusSearch.UNSPECIFIED, "name2").data.size)
            }
        }
    }

    @Test
    fun `test search community member`() {
        test { client ->
            val community1 = SnowflakeFactory.nextId()
            DatabaseFactory.dbQuery {
                Community.new(Community("c1", "name1", null, DEFAULT_PRIMARY_KEY, null, community1, now()))
            }.getOrThrow()
            attachSession(client) {
                assertEquals(0, client.searchCommunityMembers(community1, null, 10, null).data.size)
                client.joinCommunity(community1)
                assertEquals(1, client.searchCommunityMembers(community1, null, 10, null).data.size)
            }
        }
    }
}
