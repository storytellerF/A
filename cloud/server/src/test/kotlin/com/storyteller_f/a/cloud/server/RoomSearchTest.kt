package com.storyteller_f.a.cloud.server

import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.api.PaginationQuery
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.getCommunityRooms
import com.storyteller_f.a.client.core.getUserRooms
import com.storyteller_f.a.client.core.joinCommunity
import com.storyteller_f.a.client.core.joinRoom
import com.storyteller_f.a.client.core.searchCommunityRooms
import com.storyteller_f.a.client.core.searchCurrentUserRooms
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.PrimaryKey
import kotlin.test.Test

class RoomSearchTest {

    @Test
    fun `test search joined rooms by name`() = test {
        val sessionOuterTuple = attachSession {
            val communityId = createCommunityForTest().id
            val room1Id = createPublicRoomForTest(communityId, "r1", "game room").id
            val room2Id = createPublicRoomForTest(communityId, "r2", "study room").id
            val room3Id = createPublicRoomForTest(communityId, "r3", "chat room").id
            communityId to listOf(room1Id, room2Id, room3Id)
        }
        val communityId = sessionOuterTuple.custom.first
        val (room1Id, room2Id, room3Id) = sessionOuterTuple.custom.second

        attachSession {
            joinCommunity(communityId).getOrThrow()
            joinRoom(room1Id).getOrThrow()
            joinRoom(room2Id).getOrThrow()
            joinRoom(room3Id).getOrThrow()

            // 测试搜索所有已加入的房间
            expectedRoomCountForJoinedRoomList(3)

            // 测试按名称搜索已加入的房间
            expectedRoomCountForJoinedRooms(1, "game")
            expectedRoomCountForJoinedRooms(1, "study")
            expectedRoomCountForJoinedRooms(1, "chat")

            // 测试部分匹配
            expectedRoomCountForJoinedRooms(3, "room")
        }
    }

    @Test
    fun `test search rooms with member search service`() = test {
        val sessionOuterTuple = attachSession {
            val communityId = createCommunityForTest().id
            val room1Id = createPublicRoomForTest(communityId, "r1", "dragon ball").id
            val room2Id = createPublicRoomForTest(communityId, "r2", "one piece").id
            val room3Id = createPublicRoomForTest(communityId, "r3", "naruto").id
            communityId to listOf(room1Id, room2Id, room3Id)
        }
        val communityId = sessionOuterTuple.custom.first
        val (room1Id, room2Id, room3Id) = sessionOuterTuple.custom.second

        attachSession {
            joinCommunity(communityId).getOrThrow()
            joinRoom(room1Id).getOrThrow()
            joinRoom(room2Id).getOrThrow()

            // 测试搜索已加入的房间，使用memberSearchService
            expectedRoomCountForJoinedRoomList(2)
            expectedRoomCountForJoinedRooms(1, "dragon")
            expectedRoomCountForJoinedRooms(1, "piece")

            // 测试未加入的房间不会出现在结果中
            expectedRoomCountForJoinedRooms(0, "naruto")

            // 加入第三个房间后再搜索
            joinRoom(room3Id).getOrThrow()
            expectedRoomCountForJoinedRooms(1, "naruto")
            expectedRoomCountForJoinedRoomList(3)
        }
    }

    @Test
    fun `test private room cannot be searched unless joined`() = test {
        val sessionOuterTuple = attachSession {
            val privateRoomId = createPrivateRoomForTest().id
            expectedRoomCountForJoinedRoomList(1)
            expectedRoomCountForJoinedRooms(1, "name")
            privateRoomId
        }
        val privateRoomId = sessionOuterTuple.custom

        // 测试未加入的私有房间不应出现在搜索结果中
        val secondTuple = attachSession {
            // 搜索所有房间（不指定社区），私有房间不应出现
            expectedRoomCountForAllRoomList(0)

            // 尝试通过名称搜索私有房间，也不应出现（使用roomSearchService）
            expectedRoomCountForAllRooms(0, "name")

            expectedRoomCountForJoinedRoomList(0)
            expectedRoomCountForJoinedRooms(0, "name")

            // 即使使用 UNSPECIFIED 搜索，未加入的私有房间也不应出现
            expectedRoomCountForAllRooms(0, "name")
        }

        loginSession(sessionOuterTuple) {
            createJoinRoomTitleForTest(privateRoomId, secondTuple.uid)
        }

        loginSession(secondTuple) {
            joinRoom(privateRoomId).getOrThrow()
            expectedRoomCountForJoinedRoomList(1)
            expectedRoomCountForJoinedRooms(1, "name")
        }
    }

    @Test
    fun `test search rooms in community`() = test {
        val sessionOuterTuple = attachSession {
            val communityId = createCommunityForTest().id
            val room1Id = createPublicRoomForTest(communityId, "r1", "game room").id
            val room2Id = createPublicRoomForTest(communityId, "r2", "study room").id
            val room3Id = createPublicRoomForTest(communityId, "r3", "chat room").id
            communityId to listOf(room1Id, room2Id, room3Id)
        }
        val communityId = sessionOuterTuple.custom.first
        val (room1Id, room2Id, room3Id) = sessionOuterTuple.custom.second

        attachSession {
            joinCommunity(communityId).getOrThrow()
            joinRoom(room1Id).getOrThrow()
            joinRoom(room2Id).getOrThrow()

            // 测试搜索社区中的所有房间
            expectedCommunityRoomCount(3, communityId)

            // 测试按名称搜索社区中的房间
            expectedCommunityRoomCount(1, communityId, word = "game")
            expectedCommunityRoomCount(1, communityId, word = "study")
            expectedCommunityRoomCount(1, communityId, word = "chat") // 未加入的房间不应出现

            // 测试搜索已加入的房间
            expectedCommunityRoomCount(2, communityId, joinStatusSearch = JoinStatusSearch.JOINED)
            expectedCommunityRoomCount(1, communityId, word = "game", joinStatusSearch = JoinStatusSearch.JOINED)
            expectedCommunityRoomCount(1, communityId, word = "study", joinStatusSearch = JoinStatusSearch.JOINED)
            expectedCommunityRoomCount(0, communityId, word = "chat", joinStatusSearch = JoinStatusSearch.JOINED)

            // 加入第三个房间后再搜索
            joinRoom(room3Id).getOrThrow()
            expectedCommunityRoomCount(3, communityId, joinStatusSearch = JoinStatusSearch.JOINED)
            expectedCommunityRoomCount(1, communityId, word = "chat", joinStatusSearch = JoinStatusSearch.JOINED)
        }
    }

    @Test
    fun `test search user joined rooms`() = test {
        val sessionOuterTuple = attachSession {
            val communityId = createCommunityForTest().id
            val room1Id = createPublicRoomForTest(communityId, "r1", "alice room").id
            val room2Id = createPublicRoomForTest(communityId, "r2", "bob room").id
            val room3Id = createPublicRoomForTest(communityId, "r3", "charlie room").id
            communityId to listOf(room1Id, room2Id, room3Id)
        }
        val communityId = sessionOuterTuple.custom.first
        val (room1Id, room2Id, room3Id) = sessionOuterTuple.custom.second
        attachSession {
            joinCommunity(communityId).getOrThrow()
            joinRoom(room1Id).getOrThrow()
            joinRoom(room2Id).getOrThrow()

            // 测试搜索当前用户的所有房间
            expectedCurrentUserRoomCount(2)

            // 测试按名称搜索当前用户的房间
            expectedCurrentUserRoomCount(1, word = "alice")
            expectedCurrentUserRoomCount(1, word = "bob")
            expectedCurrentUserRoomCount(0, word = "charlie") // 未加入的房间不应出现

            // 加入第三个房间后再搜索
            joinRoom(room3Id).getOrThrow()
            expectedCurrentUserRoomCount(3)
            expectedCurrentUserRoomCount(1, word = "charlie")
        }
    }
}

suspend fun UserSessionManager.expectedCommunityRoomCount(
    expected: Int,
    communityId: PrimaryKey,
    word: String? = null,
    joinStatusSearch: JoinStatusSearch = JoinStatusSearch.UNSPECIFIED,
    nextRoomId: String? = null,
    size: Int = 10
) {
    val result = if (word != null) {
        searchCommunityRooms(communityId, word, joinStatusSearch, size, nextRoomId)
    } else {
        getCommunityRooms(
            communityId,
            CustomApi.Communities.Id.Rooms.CommunityRoomQuery(nextRoomId, size = size, joinStatus = joinStatusSearch)
        )
    }
    assertListSize(expected, result)
}

suspend fun UserSessionManager.expectedCurrentUserRoomCount(
    expected: Int,
    word: String? = null,
    joinStatusSearch: JoinStatusSearch = JoinStatusSearch.JOINED,
    nextRoomId: String? = null,
    size: Int = 10
) {
    val result = if (word != null) {
        searchCurrentUserRooms(word, joinStatusSearch, size, nextRoomId)
    } else {
        getUserRooms(PaginationQuery(nextRoomId, size = size))
    }
    assertListSize(expected, result)
}

suspend fun UserSessionManager.expectedUserRoomCount(
    expected: Int,
    word: String? = null,
    joinStatusSearch: JoinStatusSearch = JoinStatusSearch.UNSPECIFIED,
    nextRoomId: String? = null,
    size: Int = 10
) {
    val result = if (word != null) {
        searchCurrentUserRooms(word, joinStatusSearch, size, nextRoomId)
    } else {
        getUserRooms(PaginationQuery(nextRoomId, size = size))
    }
    assertListSize(expected, result)
}
