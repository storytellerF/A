import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.obj.NewCommunity
import com.storyteller_f.shared.obj.NewTopic
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKeyOrNull
import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.test.*

class CommunityTest {
    @Test
    fun `test get community`() = test {
        val newId = attachSession {
            createCommunity(NewCommunity("c1", "aid")).getOrThrow().id
        }.custom
        noneSession {
            val community = getCommunityInfo(newId).getOrThrow()
            assertEquals(1, community.memberCount)
            assertEquals(newId, getCommunityInfoByAid(community.aid).getOrThrow().id)
        }
    }

    @Test
    fun `test create topic in community`() = test {
        val communityId = attachSession {
            createCommunity(NewCommunity("c1", "aid")).getOrThrow().id
        }.custom
        attachSession {
            // insert community
            assertFails {
                createNewTopic(ObjectType.COMMUNITY, communityId, "hello").getOrThrow()
            }
            // 加入社区
            joinCommunity(communityId).getOrThrow()
            val communityInfo = getCommunityInfo(communityId).getOrThrow()
            // 验证加入成功
            assertTrue(communityInfo.isJoined)
            // 再次发起创建话题
            createNewTopic(ObjectType.COMMUNITY, communityId, "hello").getOrThrow()
            assertListSize(
                1,
                searchTopics(10, emptyList(), communityId, ObjectType.COMMUNITY)
            )
            assertListSize(1, getCommunityTopics(communityId, null, 10))
            // 测试上传加密话题
            assertFails {
                client.post("/topics") {
                    contentType(ContentType.Application.Json)
                    setBody(NewTopic(ObjectType.COMMUNITY, communityId, ""))
                }
            }
            // 添加话题到子话题
            kotlin.run {
                val topicId = searchTopics(10, emptyList(), communityId, ObjectType.COMMUNITY)
                    .getOrThrow().data.first().id
                val new = createNewTopic(ObjectType.TOPIC, topicId, "test").getOrThrow()
                assertEquals(ObjectType.COMMUNITY, new.rootType)
                assertEquals(communityId, new.rootId)
                val newInfo = getTopicInfo(topicId).getOrThrow()
                assertTrue(newInfo.hasComment)
            }
            // 测试退出社区
            exitCommunity(communityId)
        }
    }

    @Test
    fun `test communities pagination`() {
        test {
            val (_, _, communities) = attachSession {
                buildList {
                    repeat(10) {
                        add(createCommunity(NewCommunity("c1", "aid$it")).getOrThrow().id)
                    }
                }
            }
            attachSession {
                communities.forEach {
                    joinCommunity(it).getOrThrow()
                }
                var lastCommunityId: PrimaryKey? = null
                var sum = 0L
                while (true) {
                    val res = searchCommunity(3, JoinStatusSearch.JOINED, "", nextCommunityId = lastCommunityId)
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
    fun `test search community`() {
        test {
            val (_, _, community1Id) = attachSession {
                val info = createCommunity(NewCommunity("name1", "c1")).getOrThrow()
                createCommunity(NewCommunity("name2", "c2")).getOrThrow()
                info.id
            }
            attachSession {
                joinCommunity(community1Id)
                testSearchCommunityCount(1, null, 10, JoinStatusSearch.JOINED, null)
                testSearchCommunityCount(1, null, 10, JoinStatusSearch.NOT_JOINED, null)
                testSearchCommunityCount(2, null, 10, JoinStatusSearch.UNSPECIFIED, null)
                testSearchCommunityCount(1, null, 10, JoinStatusSearch.UNSPECIFIED, "name2")
            }
        }
    }

    private suspend fun SessionManager.testSearchCommunityCount(
        expectedCount: Int,
        nextCommunityId: PrimaryKey?,
        size: Int,
        joinStatusSearch: JoinStatusSearch,
        word: String?
    ) {
        assertEquals(
            expectedCount,
            searchCommunity(
                size,
                joinStatusSearch,
                word,
                nextCommunityId = nextCommunityId
            ).getOrThrow().data.size
        )
    }

    @Test
    fun `test search community member`() {
        test {
            val (_, _, community1) = attachSession {
                createCommunity(NewCommunity("name1", "c1")).getOrThrow().id
            }
            attachSession {
                assertListSize(1, searchCommunityMembers(community1, null, 10, null))
                joinCommunity(community1)
                assertListSize(2, searchCommunityMembers(community1, null, 10, null))
            }
        }
    }

    @Test
    fun `test get other user joined communities`() {
        test {
            val (_, id, _) = attachSession {
                repeat(10) {
                    val communityInfo = createCommunity(NewCommunity("c$it", "c$it")).getOrThrow()
                    joinCommunity(communityInfo.id).getOrThrow()
                }
            }
            noneSession {
                val response = searchCommunity(10, JoinStatusSearch.JOINED, target = id).getOrThrow()
                assertEquals(10, response.data.size)
                response.data.forEach {
                    assertFalse(it.isJoined)
                    assertNotNull(it.extension?.targetUserJoinedTime)
                }
            }
            attachSession {
                val response = searchCommunity(10, JoinStatusSearch.JOINED, target = id).getOrThrow()
                assertEquals(10, response.data.size)
                response.data.forEach {
                    joinCommunity(it.id).getOrThrow()
                }
                val response2 = searchCommunity(10, JoinStatusSearch.JOINED, target = id).getOrThrow()
                response2.data.forEach {
                    assertTrue(it.isJoined)
                    assertNotNull(it.extension?.targetUserJoinedTime)
                }
            }
        }
    }
}
