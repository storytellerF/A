package com.storyteller_f.a.cloud.server

import com.storyteller_f.a.api.core.NewCommunity
import com.storyteller_f.a.api.core.NewTopic
import com.storyteller_f.a.api.core.PaginationQuery
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.createCommunity
import com.storyteller_f.a.client.core.createTopic
import com.storyteller_f.a.client.core.exitCommunity
import com.storyteller_f.a.client.core.getCommunityInfo
import com.storyteller_f.a.client.core.getCommunityInfoByAid
import com.storyteller_f.a.client.core.getCommunityTopics
import com.storyteller_f.a.client.core.getTopicInfo
import com.storyteller_f.a.client.core.joinCommunity
import com.storyteller_f.a.client.core.searchCommunity
import com.storyteller_f.a.client.core.searchCommunityMembers
import com.storyteller_f.a.client.core.searchTopics
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
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
    fun `test join community`() = test {
        val communityId = attachSession {
            createCommunity(NewCommunity("c1", "aid")).getOrThrow().id
        }.custom
        attachSession {
            assertFalse(getCommunityInfo(communityId).getOrThrow().isJoined)
            assertFails {
                createTopic(ObjectType.COMMUNITY, communityId, "hello").getOrThrow()
            }
            joinCommunity(communityId).getOrThrow()
            assertTrue(getCommunityInfo(communityId).getOrThrow().isJoined)
            // 测试退出社区
            exitCommunity(communityId)
            assertFalse(getCommunityInfo(communityId).getOrThrow().isJoined)
        }
    }

    @Test
    fun `test create topic in community`() = test {
        val communityId = attachSession {
            createCommunity(NewCommunity("c1", "aid")).getOrThrow().id
        }.custom
        attachSession {
            joinCommunity(communityId).getOrThrow()
            createTopic(ObjectType.COMMUNITY, communityId, "hello").getOrThrow()
            assertListSize(
                1,
                searchTopics(10, parentId = communityId, parentType = ObjectType.COMMUNITY)
            )
            assertListSize(
                1,
                getCommunityTopics(
                    communityId,
                    paginationQuery = PaginationQuery(size = 10)
                )
            )
            val topicId = searchTopics(10, emptyList(), communityId, ObjectType.COMMUNITY)
                .getOrThrow().data.first().id
            val new = createTopic(ObjectType.TOPIC, topicId, "test").getOrThrow()
            assertEquals(ObjectType.COMMUNITY, new.rootType)
            assertEquals(communityId, new.rootId)
            val newInfo = getTopicInfo(topicId).getOrThrow()
            assertTrue(newInfo.hasComment)
        }
    }

    @Test
    fun `test create empty topic in community`() = test {
        attachSession {
            val communityId = createCommunity(NewCommunity("c1", "aid")).getOrThrow().id
            assertFails {
                client.post("/topics") {
                    contentType(ContentType.Application.Json)
                    setBody(NewTopic(ObjectType.COMMUNITY, communityId, ""))
                }
            }
        }
    }

    @Test
    fun `test communities pagination`() = test {
        val session = attachSession {
            buildList {
                repeat(10) {
                    add(createCommunity(NewCommunity("c1", "aid$it")).getOrThrow().id)
                }
            }
        }
        attachSession {
            session.custom.forEach {
                joinCommunity(it).getOrThrow()
            }
            var lastCommunityId: String? = null
            var sum = 0L
            while (true) {
                val res = searchCommunity(
                    3,
                    JoinStatusSearch.JOINED,
                    "",
                    nextCommunityId = lastCommunityId
                )
                    .getOrThrow()
                val pagination = res.pagination!!
                lastCommunityId = pagination.nextPageToken
                sum += res.data.size
                if (lastCommunityId == null) {
                    assertEquals(pagination.total, sum)
                    break
                }
            }
        }
    }

    @Test
    fun `test search community`() = test {
        val session = attachSession {
            val info = createCommunity(NewCommunity("name1", "c1")).getOrThrow()
            createCommunity(NewCommunity("name2", "c2")).getOrThrow()
            info.id
        }
        attachSession {
            joinCommunity(session.custom)
            testSearchCommunityCount(1, null, 10, JoinStatusSearch.JOINED, null)
            testSearchCommunityCount(1, null, 10, JoinStatusSearch.NOT_JOINED, null)
            testSearchCommunityCount(2, null, 10, JoinStatusSearch.UNSPECIFIED, null)
            testSearchCommunityCount(1, null, 10, JoinStatusSearch.UNSPECIFIED, "name2")
        }
    }

    private suspend fun UserSessionManager.testSearchCommunityCount(
        expectedCount: Int,
        nextCommunityId: String?,
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
    fun `test search community member`() = test {
        val (_, _, community1) = attachSession {
            createCommunity(NewCommunity("name1", "c1")).getOrThrow().id
        }
        attachSession {
            assertListSize(1, searchCommunityMembers(community1, null, 10, null))
            joinCommunity(community1)
            assertListSize(2, searchCommunityMembers(community1, null, 10, null))
        }
    }

    @Test
    fun `test get other user joined communities`() = test {
        val (_, id, _) = attachSession {
            repeat(10) {
                val communityInfo = createCommunity(NewCommunity("c$it", "c$it")).getOrThrow()
                joinCommunity(communityInfo.id).getOrThrow()
            }
        }
        noneSession {
            val response =
                searchCommunity(10, JoinStatusSearch.JOINED, target = id).getOrThrow()
            assertEquals(10, response.data.size)
            response.data.forEach {
                assertFalse(it.isJoined)
                assertNotNull(it.extension?.targetUserJoinedTime)
            }
        }
        attachSession {
            val response =
                searchCommunity(10, JoinStatusSearch.JOINED, target = id).getOrThrow()
            assertEquals(10, response.data.size)
            response.data.forEach {
                joinCommunity(it.id).getOrThrow()
            }
            val response2 =
                searchCommunity(10, JoinStatusSearch.JOINED, target = id).getOrThrow()
            response2.data.forEach {
                assertTrue(it.isJoined)
                assertNotNull(it.extension?.targetUserJoinedTime)
            }
        }
    }
}
