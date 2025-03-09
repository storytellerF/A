import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.hmacSign
import com.storyteller_f.shared.hmacVerify
import com.storyteller_f.shared.newHmacSha512
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.obj.NewCommunity
import com.storyteller_f.shared.obj.NewTopic
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKeyOrNull
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.*

class CommunityTest {
    @Test
    fun `test get community`() = test { client, _ ->
        val newId = attachSession(client) {
            client.createCommunity(NewCommunity("c1", "aid")).getOrThrow().id
        }.custom
        val community = client.getCommunityInfo(newId).getOrThrow()
        assertEquals(1, community.memberCount)
        assertEquals(newId, client.getCommunityInfoByAid(community.aid).getOrThrow().id)
    }

    @Test
    fun `test create topic in community`() = test { client, _ ->
        val communityId = attachSession(client) {
            client.createCommunity(NewCommunity("c1", "aid")).getOrThrow().id
        }.custom
        attachSession(client) {
            // insert community
            assertFails {
                client.createNewTopic(ObjectType.COMMUNITY, communityId, "hello").getOrThrow()
            }
            // 加入社区
            client.joinCommunity(communityId).getOrThrow()
            val communityInfo = client.getCommunityInfo(communityId, true).getOrThrow()
            // 验证加入成功
            assertTrue(communityInfo.isJoined)
            // 再次发起创建话题
            client.createNewTopic(ObjectType.COMMUNITY, communityId, "hello").getOrThrow()
            withContext(Dispatchers.IO) { delay(1000) }
            assertListSize(
                1,
                client.searchTopics(10, emptyList(), communityId, ObjectType.COMMUNITY)
            )
            assertListSize(1, client.getCommunityTopics(communityId, null, 10))
            // 测试上传加密话题
            assertFails {
                client.post("/topics") {
                    contentType(ContentType.Application.Json)
                    setBody(NewTopic(ObjectType.COMMUNITY, communityId, ""))
                }
            }
            // 添加话题到子话题
            kotlin.run {
                val topicId = client.searchTopics(10, emptyList(), communityId, ObjectType.COMMUNITY)
                    .getOrThrow().data.first().id
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
            val (_, _, _, _, communities) = attachSession(client) {
                buildList {
                    repeat(10) {
                        add(client.createCommunity(NewCommunity("c1", "aid$it")).getOrThrow().id)
                    }
                }
            }
            attachSession(client) {
                communities.forEach {
                    client.joinCommunity(it)
                }
                var lastCommunityId: PrimaryKey? = null
                var sum = 0L
                while (true) {
                    val res = client.searchCommunity(3, JoinStatusSearch.JOINED, "", nextCommunityId = lastCommunityId)
                        .getOrThrow()
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
            val (_, _, _, _, community1) = attachSession(client) {
                val info = client.createCommunity(NewCommunity("name1", "c1")).getOrThrow()
                client.createCommunity(NewCommunity("name2", "c2")).getOrThrow()
                info.id
            }
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
                size,
                joinStatusSearch,
                word,
                nextCommunityId = nextCommunityId
            ).getOrThrow().data.size
        )
    }

    @Test
    fun `test search community member`() {
        test { client, _ ->
            val (_, _, _, _, community1) = attachSession(client) {
                client.createCommunity(NewCommunity("name1", "c1")).getOrThrow().id
            }
            attachSession(client) {
                assertListSize(1, client.searchCommunityMembers(community1, null, 10, null))
                client.joinCommunity(community1)
                assertListSize(2, client.searchCommunityMembers(community1, null, 10, null))
            }
        }
    }

    @Test
    fun `test get other user joined communities`() {
        test { client, _ ->
            val (_, _, _, id, _) = attachSession(client) {
                repeat(10) {
                    val communityInfo = client.createCommunity(NewCommunity("c$it", "c$it")).getOrThrow()
                    client.joinCommunity(communityInfo.id).getOrThrow()
                }
            }
            run {
                val response = client.searchCommunity(10, JoinStatusSearch.JOINED, target = id).getOrThrow()
                assertEquals(10, response.data.size)
                response.data.forEach {
                    assertFalse(it.isJoined)
                    assertNotNull(it.extension?.targetUserJoinedTime)
                }
            }
            attachSession(client) {
                val response = client.searchCommunity(10, JoinStatusSearch.JOINED, target = id).getOrThrow()
                assertEquals(10, response.data.size)
                response.data.forEach {
                    client.joinCommunity(it.id).getOrThrow()
                }
                val response2 = client.searchCommunity(10, JoinStatusSearch.JOINED, target = id).getOrThrow()
                response2.data.forEach {
                    assertTrue(it.isJoined)
                    assertNotNull(it.extension?.targetUserJoinedTime)
                }
            }
        }
    }
}
