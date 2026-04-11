package com.storyteller_f.a.cloud.server

import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.api.NewRoom
import com.storyteller_f.a.api.NewTitle
import com.storyteller_f.a.api.PaginationQuery
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.createRoom
import com.storyteller_f.a.client.core.createTitle
import com.storyteller_f.a.client.core.exitRoom
import com.storyteller_f.a.client.core.getCommunityRooms
import com.storyteller_f.a.client.core.getRoomInfo
import com.storyteller_f.a.client.core.getRoomInfoByAid
import com.storyteller_f.a.client.core.getUserRooms
import com.storyteller_f.a.client.core.joinCommunity
import com.storyteller_f.a.client.core.joinRoom
import com.storyteller_f.a.client.core.searchCommunityRooms
import com.storyteller_f.a.client.core.searchCurrentUserRooms
import com.storyteller_f.a.client.core.searchRoomMembers
import com.storyteller_f.a.client.core.sendFrame
import com.storyteller_f.a.client.core.updateRoomInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.obj.CustomAnswer
import com.storyteller_f.shared.obj.CustomOffer
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.obj.UpdateRoomBody
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class RoomTest {
    @Test
    fun `test get room`() = test {
        val roomId = attachSession {
            val id = createCommunityForTest().id
            createPublicRoomForTest(id, "r1", "name1").id
        }.custom
        noneSession {
            assertEquals(1, getRoomInfo(roomId).getOrThrow().memberCount)
            getRoomInfoByAid("r1").getOrThrow()
        }
    }

    @Test
    fun `test joined private room count`() = test {
        val firstTuple = attachSession {
            createPrivateRoomForTest().id
        }
        val privateRoomId = firstTuple.custom
        val secondTuple = attachSession {
            assertFails {
                joinRoom(privateRoomId).getOrThrow()
            }
        }
        loginSession(firstTuple) {
            createJoinRoomTitleForTest(privateRoomId, secondTuple.uid)
        }
        loginSession(secondTuple) {
            joinRoom(privateRoomId).getOrThrow()
            expectedRoomCountForJoinedRoomList(1)
        }
    }

    @Test
    fun `test join public room count`() = test {
        val firstTuple = attachSession {
            val communityId = createCommunityForTest().id
            val publicRoom1Id = createPublicRoomForTest(communityId, "r1", "name1").id
            val publicRoom2Id = createPublicRoomForTest(communityId, "r2", "name2").id
            communityId to listOf(publicRoom1Id, publicRoom2Id)
        }
        val (publicRoom1Id, publicRoom2Id) = firstTuple.custom.second
        val communityId = firstTuple.custom.first
        attachSession {
            joinCommunity(communityId).getOrThrow()
            joinRoom(publicRoom1Id).getOrThrow()
            expectedRoomCountForJoinedRoomList(1)
            expectedRoomCountForCommunityRoomList(2, communityId, JoinStatusSearch.UNSPECIFIED)
            joinRoom(publicRoom2Id).getOrThrow()
            expectedRoomCountForJoinedRoomList(2)
            expectedRoomCountForCommunityRoomList(2, communityId, JoinStatusSearch.UNSPECIFIED)
            exitRoom(publicRoom1Id).getOrThrow()
            // 测试幂等
            exitRoom(publicRoom1Id).getOrThrow()
            expectedRoomCountForJoinedRoomList(1)
            expectedRoomCountForCommunityRoomList(2, communityId, JoinStatusSearch.UNSPECIFIED)
        }
    }

    @Test
    fun `test private room join`() = test {
        val sessionOuterTuple = attachSession {
            createPrivateRoomForTest().id
        }
        val privateRoomId = sessionOuterTuple.custom
        val secondTuple = attachSession {
            assertFails {
                joinRoom(privateRoomId).getOrThrow()
            }
        }
        loginSession(sessionOuterTuple) {
            createJoinRoomTitleForTest(privateRoomId, secondTuple.uid)
        }
        loginSession(secondTuple) {
            joinRoom(privateRoomId).getOrThrow()
            assertListSize(2, searchRoomMembers(privateRoomId, null, 10, ""))
        }
    }

    @Test
    fun `test expired private room join title cannot join`() = test {
        val user1 = attachSession {
            createPrivateRoomForTest().id
        }
        val privateRoomId = user1.custom

        val user2 = attachSession {
            assertFails {
                joinRoom(privateRoomId).getOrThrow()
            }
        }
        loginSession(user1) {
            createTitle(
                NewTitle(
                    "join-expired",
                    TitleType.JOIN,
                    user2.uid,
                    privateRoomId,
                    ObjectType.ROOM,
                    "expired invite",
                    LocalDateTime.parse("2000-01-01T00:00:00")
                )
            ).getOrThrow()
        }
        loginSession(user2) {
            assertFails {
                joinRoom(privateRoomId).getOrThrow()
            }
        }
    }

    @Test
    fun `test public room join`() = test {
        val sessionOuterTuple = attachSession {
            val communityId = createCommunityForTest().id
            val publicRoomId = createPublicRoomForTest(communityId, "r1", "name1").id
            communityId to publicRoomId
        }
        val communityId = sessionOuterTuple.custom.first
        val publicRoomId = sessionOuterTuple.custom.second
        attachSession {
            assertFails {
                joinRoom(publicRoomId).getOrThrow()
            }
            joinCommunity(communityId).getOrThrow()
            joinRoom(publicRoomId).getOrThrow()
            // 检查幂等
            joinRoom(publicRoomId).getOrThrow()
        }
    }

    @Test
    fun `test update room`() = test {
        attachSession {
            val id = createCommunityForTest().id
            val roomId = createPublicRoomForTest(id, "r1", "name1").id
            updateRoomInfo(roomId, UpdateRoomBody("new-name")).getOrThrow()
            assertEquals("new-name", getRoomInfo(roomId).getOrThrow().name)
        }
    }

    @Test
    fun `test rtc`() = test {
        val firstUser = attachSession()
        val secondUser = attachSession {
            val roomInfo = createRoom(NewRoom("test-rtc", "rtc")).getOrThrow()
            createJoinRoomTitleForTest(roomInfo.id, firstUser.uid)
            roomInfo
        }
        loginSession(firstUser) {
            joinRoom(secondUser.custom.id).getOrThrow()
        }
        val list = CopyOnWriteArrayList<RoomFrame>()
        coroutineScope {
            launch {
                loginSession(secondUser, { frame, _, session ->
                    list.add(frame)
                    processRTCMessage(frame, session)
                }) {
                    waitAndSend {
                        sendFrame(RoomFrame.StartCall(secondUser.custom.id))
                    }
                    waitRTCAnswer(list)
                }
            }
            launch {
                loginSession(firstUser, { frame, _, session ->
                    list.add(frame)
                    processRTCMessage(frame, session)
                }) {
                    waitAndSend {
                        sendFrame(RoomFrame.StartCall(secondUser.custom.id))
                    }
                    waitRTCAnswer(list)
                }
            }
        }
    }

    @Test
    fun `test rtc multi user`() = test {
        rtcSession.clear()
        val firstUser = attachSession()
        val thirdUser = attachSession()
        val secondUser = attachSession {
            val roomInfo = createRoom(NewRoom("test-rtc-multi", "rtc-multi")).getOrThrow()
            createJoinRoomTitleForTest(roomInfo.id, firstUser.uid)
            createJoinRoomTitleForTest(roomInfo.id, thirdUser.uid)
            roomInfo
        }
        loginSession(firstUser) {
            joinRoom(secondUser.custom.id).getOrThrow()
        }
        loginSession(thirdUser) {
            joinRoom(secondUser.custom.id).getOrThrow()
        }

        val roomId = secondUser.custom.id
        val list = runRtcCalls(roomId, secondUser, firstUser, thirdUser)

        assertTrue(list.count { it is RoomFrame.CreateAnswer } >= 3)
        assertTrue(list.count { it is RoomFrame.RespondAnswer } >= 3)

        stopRtcCall(thirdUser, roomId)
        assertRtcUserCleanup(roomId, thirdUser.uid)
    }

    @Test
    fun `test rtc mute state sync`() = test {
        rtcSession.clear()
        val firstUser = attachSession()
        val secondUser = attachSession {
            val roomInfo = createRoom(NewRoom("test-rtc-mute", "rtc-mute")).getOrThrow()
            createJoinRoomTitleForTest(roomInfo.id, firstUser.uid)
            roomInfo
        }
        loginSession(firstUser) {
            joinRoom(secondUser.custom.id).getOrThrow()
        }

        val roomId = secondUser.custom.id
        val list = CopyOnWriteArrayList<RoomFrame>()
        coroutineScope {
            launch {
                loginSession(secondUser, { frame, _, session ->
                    list.add(frame)
                    processRTCMessage(frame, session)
                }) {
                    waitAndSend {
                        sendFrame(RoomFrame.StartCall(roomId))
                    }
                    waitRTCAnswer(list)
                    waitAndSend {
                        sendFrame(RoomFrame.UpdateCallMediaState(roomId, audioMuted = true, videoMuted = false))
                    }
                    waitForRtcFrame(list) { frame ->
                        frame is RoomFrame.PeerMediaState && frame.uid == secondUser.uid && frame.audioMuted
                    }
                }
            }
            launch {
                loginSession(firstUser, { frame, _, session ->
                    list.add(frame)
                    processRTCMessage(frame, session)
                }) {
                    waitAndSend {
                        sendFrame(RoomFrame.StartCall(roomId))
                    }
                    waitForRtcFrame(list) { frame ->
                        frame is RoomFrame.PeerMediaState && frame.uid == secondUser.uid && frame.audioMuted
                    }
                }
            }
        }
    }
}

private suspend fun TestMate.runRtcCalls(
    roomId: PrimaryKey,
    vararg users: SessionOuterTuple<*>,
): CopyOnWriteArrayList<RoomFrame> {
    val list = CopyOnWriteArrayList<RoomFrame>()
    coroutineScope {
        users.forEach { tuple ->
            launch {
                loginSession(tuple, { frame, _, session ->
                    list.add(frame)
                    processRTCMessage(frame, session)
                }) {
                    waitAndSend {
                        sendFrame(RoomFrame.StartCall(roomId))
                    }
                    waitRTCAnswerCount(list, 3)
                }
            }
        }
    }
    return list
}

private suspend fun TestMate.stopRtcCall(
    tuple: SessionOuterTuple<*>,
    roomId: PrimaryKey,
) {
    loginSession(tuple) {
        waitAndSend {
            sendFrame(RoomFrame.StopCall(roomId))
        }
    }
    withContext(Dispatchers.IO) {
        delay(200)
    }
}

private fun assertRtcUserCleanup(
    roomId: PrimaryKey,
    uid: PrimaryKey,
) {
    val session = rtcSession[roomId]
    assertEquals(2, session?.uidList?.size)
    assertTrue(session?.socketMap?.containsKey(uid) == false)
    assertTrue(session?.offerList?.containsKey(uid) == false)
    assertTrue(session?.offerList?.values?.none { it.containsKey(uid) } == true)
    assertTrue(session?.answerList?.containsKey(uid) == false)
    assertTrue(session?.answerList?.values?.none { it.containsKey(uid) } == true)
}

suspend fun UserSessionManager.createPrivateRoomForTest(): RoomInfo = createRoom(NewRoom("name", "r3")).getOrThrow()

suspend fun UserSessionManager.createPublicRoomForTest(
    communityId: PrimaryKey,
    roomAid: String,
    roomName: String
): RoomInfo = createRoom(NewRoom(roomName, roomAid, communityId = communityId)).getOrThrow()

suspend fun UserSessionManager.expectedRoomCountForJoinedRooms(
    expected: Int,
    word: String,
    nextRoomId: String? = null,
) {
    val result = searchCurrentUserRooms(word, 10, nextRoomId)
    assertListSize(expected, result)
}

suspend fun UserSessionManager.expectedRoomCountForAllRooms(
    expected: Int,
    word: String,
    joinStatusSearch: JoinStatusSearch = JoinStatusSearch.UNSPECIFIED,
    nextRoomId: String? = null,
    communityId: PrimaryKey? = null,
) {
    val result = if (communityId != null) {
        // 公开聊天室通过 communities/{id}/rooms/search 查询
        searchCommunityRooms(communityId, word, joinStatusSearch, 10, nextRoomId)
    } else {
        // 搜索本人已加入的房间使用 users/joined-rooms/search
        searchCurrentUserRooms(word, 10, nextRoomId)
    }
    assertListSize(expected, result)
}

suspend fun UserSessionManager.expectedRoomCountForJoinedRoomList(
    expected: Int,
    nextRoomId: String? = null,
) {
    val result = getUserRooms(PaginationQuery(nextRoomId, size = 10))
    assertListSize(expected, result)
}

/**
 * 用于搜索社区中的所有房间（不带关键词）
 */
suspend fun UserSessionManager.expectedRoomCountForCommunityRoomList(
    expected: Int,
    communityId: PrimaryKey,
    joinStatusSearch: JoinStatusSearch = JoinStatusSearch.UNSPECIFIED,
    nextRoomId: String? = null,
) {
    // 公开聊天室通过 communities/{id}/rooms 查询
    val result = getCommunityRooms(
        communityId,
        CustomApi.Communities.Id.Rooms.CommunityRoomQuery(nextRoomId, size = 10, joinStatus = joinStatusSearch)
    )
    assertListSize(expected, result)
}

/**
 * 用于搜索当前用户的所有房间（不带关键词）
 */
suspend fun UserSessionManager.expectedRoomCountForAllRoomList(
    expected: Int,
    nextRoomId: String? = null,
) {
    // 搜索本人已加入的房间使用 users/rooms
    val result = getUserRooms(PaginationQuery(nextRoomId, size = 10))
    assertListSize(expected, result)
}

suspend fun UserSessionManager.createJoinRoomTitleForTest(
    privateRoomId: PrimaryKey,
    uid: PrimaryKey
): TitleInfo = createTitle(
    NewTitle("invite", TitleType.JOIN, uid, privateRoomId, ObjectType.ROOM, "invite for test")
).getOrThrow()

suspend fun waitRTCAnswer(list: MutableList<RoomFrame>) {
    waitRTCAnswerCount(list, 1)
}

suspend fun waitRTCAnswerCount(list: MutableList<RoomFrame>, expectedCount: Int) {
    waitForRtcFrame(list) {
        list.count { frame -> frame is RoomFrame.RespondAnswer } >= expectedCount
    }
}

suspend fun waitForRtcFrame(
    list: MutableList<RoomFrame>,
    predicate: (RoomFrame) -> Boolean,
) {
    var i = 0
    while (i < 10) {
        i++
        if (list.any(predicate)) {
            break
        }
        withContext(Dispatchers.IO) {
            delay(1000)
        }
    }
    assertTrue(list.any(predicate))
}

suspend fun processRTCMessage(frame: RoomFrame, session: DefaultClientWebSocketSession) {
    if (frame is RoomFrame.CreateOffer) {
        session.sendFrame(RoomFrame.SendOffer(CustomOffer("offer"), frame.roomId, frame.targetUid))
    } else if (frame is RoomFrame.CreateAnswer) {
        session.sendFrame(RoomFrame.SendAnswer(CustomAnswer("answer"), frame.roomId, frame.targetUid))
    }
}
