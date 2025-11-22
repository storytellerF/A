package com.storyteller_f.a.cloud.server

import com.storyteller_f.a.client.core.joinCommunity
import com.storyteller_f.a.client.core.joinRoom
import com.storyteller_f.shared.type.JoinStatusSearch
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
            expectedRoomCount(3, JoinStatusSearch.JOINED)

            // 测试按名称搜索已加入的房间
            expectedRoomCount(1, JoinStatusSearch.JOINED, word = "game")
            expectedRoomCount(1, JoinStatusSearch.JOINED, word = "study")
            expectedRoomCount(1, JoinStatusSearch.JOINED, word = "chat")

            // 测试部分匹配
            expectedRoomCount(3, JoinStatusSearch.JOINED, word = "room")
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
            expectedRoomCount(2, JoinStatusSearch.JOINED)
            expectedRoomCount(1, JoinStatusSearch.JOINED, word = "dragon")
            expectedRoomCount(1, JoinStatusSearch.JOINED, word = "piece")

            // 测试未加入的房间不会出现在结果中
            expectedRoomCount(0, JoinStatusSearch.JOINED, word = "naruto")

            // 加入第三个房间后再搜索
            joinRoom(room3Id).getOrThrow()
            expectedRoomCount(1, JoinStatusSearch.JOINED, word = "naruto")
            expectedRoomCount(3, JoinStatusSearch.JOINED)
        }
    }
}
