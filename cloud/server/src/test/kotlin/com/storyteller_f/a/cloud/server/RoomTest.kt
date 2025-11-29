package com.storyteller_f.a.cloud.server

import com.storyteller_f.a.api.NewRoom
import com.storyteller_f.a.api.NewTitle
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.createRoom
import com.storyteller_f.a.client.core.createTitle
import com.storyteller_f.a.client.core.exitRoom
import com.storyteller_f.a.client.core.getRoomInfo
import com.storyteller_f.a.client.core.getRoomInfoByAid
import com.storyteller_f.a.client.core.joinCommunity
import com.storyteller_f.a.client.core.joinRoom
import com.storyteller_f.a.client.core.searchRoomMembers
import com.storyteller_f.a.client.core.searchRooms
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

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
            expectedRoomCount(1, JoinStatusSearch.JOINED)
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
            expectedRoomCount(1, JoinStatusSearch.JOINED)
            expectedRoomCount(2, JoinStatusSearch.UNSPECIFIED, communityId = communityId)
            joinRoom(publicRoom2Id).getOrThrow()
            expectedRoomCount(2, JoinStatusSearch.JOINED)
            expectedRoomCount(2, JoinStatusSearch.UNSPECIFIED, communityId = communityId)
            exitRoom(publicRoom1Id).getOrThrow()
            // 测试幂等
            exitRoom(publicRoom1Id).getOrThrow()
            expectedRoomCount(1, JoinStatusSearch.JOINED, communityId = communityId)
            expectedRoomCount(2, JoinStatusSearch.UNSPECIFIED, communityId = communityId)
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
            assertListSize(2, searchRoomMembers(privateRoomId, null, 10, null))
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
        val list = mutableListOf<RoomFrame>()
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
}

suspend fun UserSessionManager.createPrivateRoomForTest(): RoomInfo = createRoom(NewRoom("name", "r3")).getOrThrow()

suspend fun UserSessionManager.createPublicRoomForTest(
    communityId: PrimaryKey,
    roomAid: String,
    roomName: String
): RoomInfo = createRoom(NewRoom(roomName, roomAid, communityId = communityId)).getOrThrow()

suspend fun UserSessionManager.expectedRoomCount(
    expected: Int,
    joinStatusSearch: JoinStatusSearch,
    nextRoomId: String? = null,
    word: String? = null,
    communityId: PrimaryKey? = null,
) {
    assertListSize(expected, searchRooms(joinStatusSearch, 10, nextRoomId, word, communityId))
}

suspend fun UserSessionManager.createJoinRoomTitleForTest(
    privateRoomId: PrimaryKey,
    uid: PrimaryKey
): TitleInfo = createTitle(
    NewTitle("invite", TitleType.JOIN, uid, privateRoomId, ObjectType.ROOM, "invite for test")
).getOrThrow()

suspend fun waitRTCAnswer(list: MutableList<RoomFrame>) {
    var i = 0
    while (i < 10) {
        i++
        if (list.firstOrNull {
                it is RoomFrame.RespondAnswer
            } != null) {
            break
        }
        withContext(Dispatchers.IO) {
            delay(1000)
        }
    }
}

suspend fun processRTCMessage(frame: RoomFrame, session: DefaultClientWebSocketSession) {
    if (frame is RoomFrame.CreateOffer) {
        session.sendFrame(RoomFrame.SendOffer(CustomOffer("offer"), frame.roomId, frame.targetUid))
    } else if (frame is RoomFrame.CreateAnswer) {
        session.sendFrame(RoomFrame.SendAnswer(CustomAnswer("answer"), frame.roomId, frame.targetUid))
    }
}
