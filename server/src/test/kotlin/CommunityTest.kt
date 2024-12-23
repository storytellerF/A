import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.hmacSign
import com.storyteller_f.shared.hmacVerify
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.newHmacSha512
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.obj.NewTopic
import com.storyteller_f.shared.type.DEFAULT_PRIMARY_KEY
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKeyOrNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.Community
import com.storyteller_f.tables.createCommunity
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class CommunityTest {
    @Test
    fun `test get community`() = test { client, _ ->
        val newId = SnowflakeFactory.nextId()
        createCommunity(
            Community("aid", "name", null, DEFAULT_PRIMARY_KEY, null, newId, now())
        ).getOrThrow()
        val community = client.getCommunityInfo(newId).getOrThrow()
        assertEquals(newId, client.getCommunityInfoByAid(community.aid).getOrThrow().id)
    }

    @Test
    fun `test create topic in community`() = test { client, _ ->
        attachSession(client) {
            // insert community
            val communityId = SnowflakeFactory.nextId()
            val community = Community("aid", "name", null, DEFAULT_PRIMARY_KEY, null, communityId, now())
            createCommunity(community).getOrThrow()
            assertFails {
                client.createNewTopic(ObjectType.COMMUNITY, communityId, "hello").getOrThrow()
            }
            // 加入社区
            client.joinCommunity(communityId)
            val communityInfo = client.getCommunityInfo(communityId, true).getOrThrow()
            // 验证加入成功
            assertTrue(communityInfo.isJoined)
            // 再次发起创建话题
            client.createNewTopic(ObjectType.COMMUNITY, communityId, "hello").getOrThrow()
            assertEquals(1, client.getCommunityTopics(communityId, null, 10).getOrThrow().data.size)
            // 测试上传加密话题
            assertFails {
                client.post("/topics") {
                    contentType(ContentType.Application.Json)
                    setBody(NewTopic(ObjectType.COMMUNITY, communityId, TopicContent.Encrypted("", emptyMap())))
                }
            }
            // 添加话题到子话题
            kotlin.run {
                val topicId = client.getCommunityTopics(communityId, null, 10).getOrThrow().data.first().id
                val new = client.createNewTopic(ObjectType.TOPIC, topicId, "test").getOrThrow()
                assertEquals(ObjectType.COMMUNITY, new.rootType)
                assertEquals(communityId, new.rootId)
                val newInfo = client.getTopicInfo(topicId).getOrThrow()
                assertTrue(newInfo.hasComment)
            }
            // 测试退出社区
            client.exitCommunity(communityId)
        }
    }

    @Test
    fun `test communities pagination`() {
        test { client, _ ->
            val communities = buildList {
                repeat(10) {
                    val newId = SnowflakeFactory.nextId()
                    createCommunity(
                        Community("aid$it", "name", null, DEFAULT_PRIMARY_KEY, null, newId, now())
                    ).getOrThrow()
                    add(newId)
                }
            }
            attachSession(client) {
                communities.forEach {
                    client.joinCommunity(it)
                }
                var lastCommunityId: PrimaryKey? = null
                var sum = 0L
                while (true) {
                    val res = client.searchCommunity(lastCommunityId, 3, JoinStatusSearch.JOINED, "").getOrThrow()
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
        test { client, _ ->
            val community1 = SnowflakeFactory.nextId()
            createCommunity(Community("c1", "name1", null, DEFAULT_PRIMARY_KEY, null, community1, now())).getOrThrow()
            val community2 = SnowflakeFactory.nextId()
            createCommunity(Community("c2", "name2", null, DEFAULT_PRIMARY_KEY, null, community2, now())).getOrThrow()
            attachSession(client) {
                client.joinCommunity(community1)
                testSearchCommunityCount(client, 1, null, 10, JoinStatusSearch.JOINED, null)
                testSearchCommunityCount(client, 1, null, 10, JoinStatusSearch.NOT_JOINED, null)
                testSearchCommunityCount(client, 2, null, 10, JoinStatusSearch.UNSPECIFIED, null)
                testSearchCommunityCount(client, 1, null, 10, JoinStatusSearch.UNSPECIFIED, "name2")
            }
        }
    }

    private suspend fun testSearchCommunityCount(
        client: HttpClient,
        expectedCount: Int,
        nextCommunityId: PrimaryKey?,
        size: Int,
        joinStatusSearch: JoinStatusSearch,
        word: String?
    ) {
        assertEquals(
            expectedCount,
            client.searchCommunity(
                nextCommunityId,
                size,
                joinStatusSearch,
                word
            ).getOrThrow().data.size
        )
    }

    @Test
    fun `test search community member`() {
        test { client, _ ->
            val community1 = SnowflakeFactory.nextId()
            createCommunity(Community("c1", "name1", null, DEFAULT_PRIMARY_KEY, null, community1, now())).getOrThrow()
            attachSession(client) {
                assertEquals(0, client.searchCommunityMembers(community1, null, 10, null).getOrThrow().data.size)
                client.joinCommunity(community1)
                assertEquals(1, client.searchCommunityMembers(community1, null, 10, null).getOrThrow().data.size)
            }
        }
    }
}
