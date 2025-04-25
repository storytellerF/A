import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.obj.NewCommunity
import com.storyteller_f.shared.obj.NewRoom
import com.storyteller_f.shared.obj.NewTitle
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.TitleType
import io.ktor.client.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class RoomTest {
    @Test
    fun `test get room`() {
        test { client, _ ->
            val roomId = attachSession(client) {
                val id = client.createCommunity(NewCommunity("name1", "c1")).getOrThrow().id
                client.createRoom(NewRoom("name1", "r1", communityId = id)).getOrThrow().id
            }.custom
            assertEquals(1, client.getRoomInfo(roomId).getOrThrow().memberCount)
            client.getRoomInfoByAid("r1").getOrThrow()
        }
    }

    @Test
    fun `test room search`() {
        test { client, _ ->
            val firstTuple = attachSession(client) {
                val communityId = client.createCommunity(NewCommunity("name1", "c1")).getOrThrow().id
                val publicRoom1Id = client.createRoom(NewRoom("name1", "r1", communityId = communityId)).getOrThrow().id
                val publicRoom2Id = client.createRoom(NewRoom("name2", "r2", communityId = communityId)).getOrThrow().id
                val privateRoomId = client.createRoom(NewRoom("name3", "r3")).getOrThrow().id
                communityId to listOf(publicRoom1Id, publicRoom2Id, privateRoomId)
            }
            val (publicRoom1Id, _, privateRoomId) = firstTuple.custom.second
            val secondTuple = attachSession(client) {
                assertFails {
                    client.joinRoom(publicRoom1Id).getOrThrow()
                }
                assertFails {
                    client.joinRoom(privateRoomId).getOrThrow()
                }
                client.joinCommunity(firstTuple.custom.first)
                client.joinRoom(publicRoom1Id)
                testSearchRoom(1, 10, null, JoinStatusSearch.JOINED, null, null, client)
                testSearchRoom(4, 10, null, JoinStatusSearch.NOT_JOINED, null, null, client)
                testSearchRoom(5, 10, null, JoinStatusSearch.UNSPECIFIED, null, null, client)
                testSearchRoom(1, 10, null, JoinStatusSearch.UNSPECIFIED, "name2", null, client)
            }
            loginSession(client, firstTuple) {
                client.createTitle(
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
            loginSession(client, secondTuple) {
                client.joinRoom(privateRoomId).getOrThrow()
                testSearchRoom(2, 10, null, JoinStatusSearch.JOINED, null, null, client)
                testSearchRoom(5, 10, null, JoinStatusSearch.UNSPECIFIED, null, firstTuple.custom.first, client)
                client.exitRoom(publicRoom1Id)
                // 测试幂等
                client.exitRoom(publicRoom1Id)
            }
        }
    }

    private suspend fun testSearchRoom(
        expected: Int,
        size: Int,
        nextRoomId: PrimaryKey?,
        joinStatusSearch: JoinStatusSearch,
        word: String?,
        communityId: PrimaryKey?,
        client: HttpClient
    ) {
        assertListSize(
            expected,
            client.searchRooms(size, nextRoomId, joinStatusSearch, word, communityId)
        )
    }

    @Test
    fun `test search room members`() {
        test { client, _ ->
            val sessionOuterTuple = attachSession(client) {
                val communityId = client.createCommunity(NewCommunity("name1", "c1")).getOrThrow().id
                val publicRoomId = client.createRoom(NewRoom("name1", "r1", communityId = communityId)).getOrThrow().id
                val privateRoomId = client.createRoom(NewRoom("name3", "r3")).getOrThrow().id
                communityId to listOf(publicRoomId, privateRoomId)
            }
            val communityId = sessionOuterTuple.custom.first
            val (publicRoomId, privateRoomId) = sessionOuterTuple.custom.second
            attachSession(client) {
                client.joinCommunity(communityId)
                client.joinRoom(publicRoomId)
                // 检查幂等
                client.joinRoom(publicRoomId)
            }
            val secondTuple = attachSession(client) {
                assertListSize(2, client.searchRoomMembers(publicRoomId, null, 10, null))
                client.joinCommunity(communityId)
                client.joinRoom(publicRoomId)
                assertListSize(3, client.searchRoomMembers(publicRoomId, null, 10, null))
                assertFails {
                    client.searchRoomMembers(privateRoomId, null, 10, null).getOrThrow()
                }
                assertFails {
                    client.joinRoom(privateRoomId).getOrThrow()
                }
            }
            loginSession(client, sessionOuterTuple) {
                client.createTitle(
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
            loginSession(client, secondTuple) {
                client.joinRoom(privateRoomId).getOrThrow()
                assertListSize(2, client.searchRoomMembers(privateRoomId, null, 10, null))
            }
        }
    }
}
