import com.storyteller_f.a.client.core.*
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.obj.*
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class RoomTest {
    @Test
    fun `test get room`() {
        test {
            val roomId = attachSession {
                val id = createCommunity(NewCommunity("name1", "c1")).getOrThrow().id
                createRoom(NewRoom("name1", "r1", communityId = id)).getOrThrow().id
            }.custom
            noneSession {
                assertEquals(1, getRoomInfo(roomId).getOrThrow().memberCount)
                getRoomInfoByAid("r1").getOrThrow()
            }
        }
    }

    @Test
    fun `test room search`() {
        test {
            val firstTuple = attachSession {
                val communityId = createCommunity(NewCommunity("name1", "c1")).getOrThrow().id
                val publicRoom1Id = createRoom(NewRoom("name1", "r1", communityId = communityId)).getOrThrow().id
                val publicRoom2Id = createRoom(NewRoom("name2", "r2", communityId = communityId)).getOrThrow().id
                val privateRoomId = createRoom(NewRoom("name3", "r3")).getOrThrow().id
                communityId to listOf(publicRoom1Id, publicRoom2Id, privateRoomId)
            }
            val (publicRoom1Id, _, privateRoomId) = firstTuple.custom.second
            val secondTuple = attachSession {
                assertFails {
                    joinRoom(publicRoom1Id).getOrThrow()
                }
                assertFails {
                    joinRoom(privateRoomId).getOrThrow()
                }
                joinCommunity(firstTuple.custom.first)
                joinRoom(publicRoom1Id)
                testSearchRoom(1, 10, null, JoinStatusSearch.JOINED, null, null)
                testSearchRoom(4, 10, null, JoinStatusSearch.NOT_JOINED, null, null)
                testSearchRoom(5, 10, null, JoinStatusSearch.UNSPECIFIED, null, null)
                testSearchRoom(1, 10, null, JoinStatusSearch.UNSPECIFIED, "name2", null)
            }
            loginSession(firstTuple) {
                createTitle(
                    NewTitle(
                        "invite",
                        TitleType.JOIN,
                        secondTuple.uid,
                        privateRoomId,
                        ObjectType.ROOM,
                        "invite for test"
                    )
                )
                    .getOrThrow()
            }
            loginSession(secondTuple) {
                joinRoom(privateRoomId).getOrThrow()
                testSearchRoom(2, 10, null, JoinStatusSearch.JOINED, null, null)
                testSearchRoom(5, 10, null, JoinStatusSearch.UNSPECIFIED, null, firstTuple.custom.first)
                exitRoom(publicRoom1Id)
                // 测试幂等
                exitRoom(publicRoom1Id)
            }
        }
    }

    private suspend fun SessionManager.testSearchRoom(
        expected: Int,
        size: Int,
        nextRoomId: String?,
        joinStatusSearch: JoinStatusSearch,
        word: String?,
        communityId: PrimaryKey?,
    ) {
        assertListSize(
            expected,
            searchRooms(size, nextRoomId, joinStatusSearch, word, communityId)
        )
    }

    @Test
    fun `test search room members`() {
        test {
            val sessionOuterTuple = attachSession {
                val communityId = createCommunity(NewCommunity("name1", "c1")).getOrThrow().id
                val publicRoomId = createRoom(NewRoom("name1", "r1", communityId = communityId)).getOrThrow().id
                val privateRoomId = createRoom(NewRoom("name3", "r3")).getOrThrow().id
                communityId to listOf(publicRoomId, privateRoomId)
            }
            val communityId = sessionOuterTuple.custom.first
            val (publicRoomId, privateRoomId) = sessionOuterTuple.custom.second
            attachSession {
                joinCommunity(communityId)
                joinRoom(publicRoomId)
                // 检查幂等
                joinRoom(publicRoomId)
            }
            val secondTuple = attachSession {
                assertListSize(2, searchRoomMembers(publicRoomId, null, 10, null))
                joinCommunity(communityId)
                joinRoom(publicRoomId)
                assertListSize(3, searchRoomMembers(publicRoomId, null, 10, null))
                assertFails {
                    searchRoomMembers(privateRoomId, null, 10, null).getOrThrow()
                }
                assertFails {
                    joinRoom(privateRoomId).getOrThrow()
                }
            }
            loginSession(sessionOuterTuple) {
                createTitle(
                    NewTitle(
                        "invite",
                        TitleType.JOIN,
                        secondTuple.uid,
                        privateRoomId,
                        ObjectType.ROOM,
                        "invite for test"
                    )
                )
                    .getOrThrow()
            }
            loginSession(secondTuple) {
                joinRoom(privateRoomId).getOrThrow()
                assertListSize(2, searchRoomMembers(privateRoomId, null, 10, null))
            }
        }
    }

    @Test
    fun `test update room`() {
        test {
            attachSession {
                val id = createCommunity(NewCommunity("name1", "c1")).getOrThrow().id
                val roomId = createRoom(NewRoom("name1", "r1", communityId = id)).getOrThrow().id
                updateRoomInfo(roomId, UpdateRoomBody("new name")).getOrThrow()
                assertEquals("new name", getRoomInfo(roomId).getOrThrow().name)
            }
        }
    }

    @Test
    fun `test web rtc`() {
        test {
            val firstSession = attachSession {
            }
            val secondSession = attachSession {
                val roomInfo = createRoom(NewRoom("name1", "r1")).getOrThrow()
                createTitle(
                    NewTitle(
                        "test",
                        TitleType.JOIN,
                        firstSession.uid,
                        roomInfo.id,
                        ObjectType.ROOM,
                        "hello"
                    )
                ).getOrThrow()
                roomInfo
            }
            loginSession(firstSession) {
                joinRoom(secondSession.custom.id).getOrThrow()
            }
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    @Test
    fun `test rtc`() {
        test {
            val firstUser = attachSession {
            }
            val secondUser = attachSession {
                val roomInfo = createRoom(NewRoom("test rtc", "rtc")).getOrThrow()
                createTitle(
                    NewTitle(
                        "test",
                        TitleType.JOIN,
                        firstUser.uid,
                        roomInfo.id,
                        ObjectType.ROOM,
                        "hello"
                    )
                ).getOrThrow()
                roomInfo
            }
            coroutineScope {
                launch {
                    val list = mutableListOf<RoomFrame>()
                    loginSession(secondUser, { frame, model ->
                        list.add(frame)
                    }) {
                        while (true) {
                            if (webSocketClient.connectionHandler.data.value != null) {
                                break
                            }
                            withContext(Dispatchers.IO) {
                                delay(100)
                            }
                        }
                        webSocketClient.useWebSocket {
                            sendSerialized(RoomFrame.StartCall(secondUser.custom.id) as RoomFrame)
                        }?.join()
                        var i = 0
                        while (i < 10) {
                            i++
                            if (list.firstOrNull {
                                    it is RoomFrame.CreateAnswer || it is RoomFrame.CreateOffer
                                } != null) {
                                break
                            }
                            withContext(Dispatchers.IO) {
                                delay(100)
                            }
                        }
                    }
                }
                launch {
                    val list = mutableListOf<RoomFrame>()
                    loginSession(firstUser, { frame, model ->
                        list.add(frame)
                    }) {
                        joinRoom(secondUser.custom.id).getOrThrow()
                        while (true) {
                            if (webSocketClient.connectionHandler.data.value != null) {
                                break
                            }
                            withContext(Dispatchers.IO) {
                                delay(100)
                            }
                        }
                        webSocketClient.useWebSocket {
                            sendSerialized(RoomFrame.StartCall(secondUser.custom.id) as RoomFrame)
                        }?.join()
                        var i = 0
                        while (i < 10) {
                            i++
                            if (list.firstOrNull {
                                    it is RoomFrame.CreateAnswer || it is RoomFrame.CreateOffer
                                } != null) {
                                break
                            }
                            withContext(Dispatchers.IO) {
                                delay(100)
                            }
                        }
                    }
                }
            }
        }
    }
}
