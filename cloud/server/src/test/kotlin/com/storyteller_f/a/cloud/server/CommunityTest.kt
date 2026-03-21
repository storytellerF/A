package com.storyteller_f.a.cloud.server

import com.storyteller_f.a.api.CustomApi
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
import com.storyteller_f.a.client.core.getCommunityRooms
import com.storyteller_f.a.client.core.getCommunityTopics
import com.storyteller_f.a.client.core.getTopicInfo
import com.storyteller_f.a.client.core.getUserCommunities
import com.storyteller_f.a.client.core.getUserJoinedCommunities
import com.storyteller_f.a.client.core.joinCommunity
import com.storyteller_f.a.client.core.searchCommunityTopics
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.MemberPolicy
import com.storyteller_f.shared.model.TitleType
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
            createCommunityForTest().id
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
            createCommunityForTest().id
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
            createCommunityForTest().id
        }.custom
        attachSession {
            joinCommunity(communityId).getOrThrow()
            createTopic(ObjectType.COMMUNITY, communityId, "hello").getOrThrow()
            assertListSize(1, searchCommunityTopics(communityId, 10, "hello"))
            assertListSize(1, getCommunityTopics(communityId, paginationQuery = PaginationQuery(size = 10)))
            val topicId = searchCommunityTopics(communityId, 10, "hello")
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
            val communityId = createCommunityForTest().id
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
                    add(createCommunityForTest("c1", "aid$it").id)
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
                val res = getUserCommunities(
                    CustomApi.Users.JoinedCommunities.UserCommunitiesQuery(nextPageToken = lastCommunityId, size = 3)
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
    fun `test get other user joined communities`() = test {
        val id = attachSession {
            repeat(10) {
                val communityInfo = createCommunityForTest("c$it", "c$it").id
                joinCommunity(communityInfo).getOrThrow()
            }
        }.uid
        noneSession {
            val response = getUserJoinedCommunities(
                id,
                CustomApi.Users.JoinedCommunities.UserCommunitiesQuery(size = 10)
            ).getOrThrow()
            assertEquals(10, response.data.size)
            response.data.forEach {
                assertFalse(it.isJoined)
                assertNotNull(it.extension?.targetMemberInfo)
            }
        }
        attachSession {
            val response = getUserJoinedCommunities(
                id,
                CustomApi.Users.JoinedCommunities.UserCommunitiesQuery(size = 10)
            ).getOrThrow()
            assertEquals(10, response.data.size)
            response.data.forEach {
                joinCommunity(it.id).getOrThrow()
            }
            val response2 = getUserJoinedCommunities(
                id,
                CustomApi.Users.JoinedCommunities.UserCommunitiesQuery(size = 10)
            ).getOrThrow()
            response2.data.forEach {
                assertTrue(it.isJoined)
                assertNotNull(it.extension?.targetMemberInfo)
            }
        }
    }

    @Test
    fun `test community invite only`() = test {
        val firstTuple = attachSession {
            createCommunityForTest("name1", "c1", memberPolicy = MemberPolicy.INVITE_ONLY)
        }
        val communityId = firstTuple.custom.id
        val secondTuple = attachSession {
            assertFails {
                joinCommunity(communityId).getOrThrow()
            }
        }
        loginSession(firstTuple) {
            createTitle(
                NewTitle("join", TitleType.JOIN, secondTuple.uid, communityId, ObjectType.COMMUNITY, "join")
            ).getOrThrow()
        }
        loginSession(secondTuple) {
            joinCommunity(communityId).getOrThrow()
        }
    }

    @Test
    fun `test get community rooms`() = test {
        val communityId = attachSession {
            createCommunityForTest().id
        }.custom

        attachSession {
            // 测试获取社区中的所有房间
            val rooms = getCommunityRooms(communityId, CustomApi.Communities.Id.Rooms.CommunityRoomQuery())
            assertListSize(0, rooms)
        }
    }
}

suspend fun UserSessionManager.createCommunityForTest(
    name: String = "name1",
    id: String = "c1",
    memberPolicy: MemberPolicy = MemberPolicy.OPEN
): CommunityInfo = createCommunity(NewCommunity(name, id, memberPolicy = memberPolicy)).getOrThrow()
