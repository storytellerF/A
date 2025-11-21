package com.storyteller_f.a.cloud.server

import com.storyteller_f.a.api.NewCommunity
import com.storyteller_f.a.api.NewTitle
import com.storyteller_f.a.api.NewTopic
import com.storyteller_f.a.api.PaginationQuery
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.createCommunity
import com.storyteller_f.a.client.core.createTitle
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
import com.storyteller_f.shared.model.MemberPolicy
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
                ).getOrThrow()
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
    fun `test jon community search member count`() = test {
        val communityId = attachSession {
            createCommunity(NewCommunity("name1", "c1")).getOrThrow().id
        }.custom
        attachSession {
            assertListSize(1, searchCommunityMembers(communityId, null, 10, null))
            joinCommunity(communityId)
            assertListSize(2, searchCommunityMembers(communityId, null, 10, null))
        }
    }

    @Test
    fun `test get other user joined communities`() = test {
        val id = attachSession {
            repeat(10) {
                val communityInfo = createCommunity(NewCommunity("c$it", "c$it")).getOrThrow()
                joinCommunity(communityInfo.id).getOrThrow()
            }
        }.uid
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

    @Test
    fun `test community invite only`() = test {
        val firstTuple = attachSession {
            createCommunity(
                NewCommunity(
                    "name1",
                    "c1",
                    memberPolicy = MemberPolicy.INVITE_ONLY
                )
            ).getOrThrow()
        }
        val communityId = firstTuple.custom.id
        val secondTuple = attachSession {
            assertFails {
                joinCommunity(communityId).getOrThrow()
            }
        }
        loginSession(firstTuple) {
            createTitle(
                NewTitle(
                    "join",
                    TitleType.JOIN,
                    secondTuple.uid,
                    communityId,
                    ObjectType.COMMUNITY,
                    "join"
                )
            ).getOrThrow()
        }
        loginSession(secondTuple) {
            joinCommunity(communityId).getOrThrow()
        }
    }

    @Test
    fun `test search community with different strategies`() = test {
        // 创建测试数据
        val session = attachSession {
            val community1 = createCommunity(NewCommunity("test community 1", "tc1")).getOrThrow()
            val community2 = createCommunity(NewCommunity("test community 2", "tc2")).getOrThrow()
            val community3 = createCommunity(NewCommunity("another community", "ac1")).getOrThrow()
            Triple(community1.id, community2.id, community3.id)
        }

        attachSession {
            val (community1Id, community2Id, community3Id) = session.custom

            // 加入前两个社区
            joinCommunity(community1Id).getOrThrow()
            joinCommunity(community2Id).getOrThrow()

            // 测试情况1: word 为空，使用 database 查询
            // 搜索已加入的社区，不带关键字
            val result1 = searchCommunity(
                size = 10,
                joinStatusSearch = JoinStatusSearch.JOINED,
                word = null
            ).getOrThrow()
            assertEquals(2, result1.data.size, "Should find 2 joined communities when word is null")

            // 测试情况2: word 不为空 && 搜索已加入的社区，使用 memberSearchService
            // 搜索已加入的社区，带关键字"test"
            val result2 = searchCommunity(
                size = 10,
                joinStatusSearch = JoinStatusSearch.JOINED,
                word = "test"
            ).getOrThrow()
            assertEquals(2, result2.data.size, "Should find 2 joined communities matching 'test'")

            // 测试情况3: word 不为空 && 不是搜索已加入（Unspecified），使用 communitySearchService
            // 搜索所有社区，带关键字"community"
            val result3 = searchCommunity(
                size = 10,
                joinStatusSearch = JoinStatusSearch.UNSPECIFIED,
                word = "community"
            ).getOrThrow()
            assertEquals(3, result3.data.size, "Should find 3 communities matching 'community'")

            // 搜索所有社区，带关键字"another"
            val result4 = searchCommunity(
                size = 10,
                joinStatusSearch = JoinStatusSearch.UNSPECIFIED,
                word = "another"
            ).getOrThrow()
            assertEquals(1, result4.data.size, "Should find 1 community matching 'another'")
        }
    }
}
