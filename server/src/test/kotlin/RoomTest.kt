import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.obj.NewCommunity
import com.storyteller_f.shared.obj.NewRoom
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.*
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
            val custom = attachSession(client) {
                val cId = client.createCommunity(NewCommunity("name1", "c1")).getOrThrow().id
                val room1Id = client.createRoom(NewRoom("name1", "r1", communityId = cId)).getOrThrow().id
                val room2Id = client.createRoom(NewRoom("name2", "r2", communityId = cId)).getOrThrow().id
                val room3Id = client.createRoom(NewRoom("name3", "r3")).getOrThrow().id
                cId to listOf(room1Id, room2Id, room3Id)
            }.custom
            val room1 = custom.second[0]
            val room3 = custom.second[2]
            attachSession(client) {
                assertFails {
                    client.joinRoom(room1).getOrThrow()
                }
                client.joinCommunity(custom.first)
                client.joinRoom(room1)
                testSearchRoom(1, 10, null, JoinStatusSearch.JOINED, null, null, client)
                testSearchRoom(4, 10, null, JoinStatusSearch.NOT_JOINED, null, null, client)
                testSearchRoom(5, 10, null, JoinStatusSearch.UNSPECIFIED, null, null, client)
                testSearchRoom(1, 10, null, JoinStatusSearch.UNSPECIFIED, "name2", null, client)
                DatabaseFactory.createMemberJoin(MemberJoin(it.uid, room3, ObjectType.ROOM, now())).getOrThrow()
                testSearchRoom(2, 10, null, JoinStatusSearch.JOINED, null, null, client)
                testSearchRoom(5, 10, null, JoinStatusSearch.UNSPECIFIED, null, custom.first, client)
                client.exitRoom(room1)
                // 测试幂等
                client.exitRoom(room1)
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
            val custom = attachSession(client) {
                val cId = client.createCommunity(NewCommunity("name1", "c1")).getOrThrow().id
                val room1Id = client.createRoom(NewRoom("name1", "r1", communityId = cId)).getOrThrow().id
                val room3Id = client.createRoom(NewRoom("name3", "r3")).getOrThrow().id
                cId to listOf(room1Id, room3Id)
            }.custom
            val communityId = custom.first
            val publicRoom = custom.second[0]
            val privateRoom = custom.second[1]
            attachSession(client) {
                client.joinCommunity(communityId)
                client.joinRoom(publicRoom)
                // 检查幂等
                client.joinRoom(publicRoom)
            }
            attachSession(client) {
                assertListSize(2, client.searchRoomMembers(publicRoom, null, 10, null))
                client.joinCommunity(communityId)
                client.joinRoom(publicRoom)
                assertListSize(3, client.searchRoomMembers(publicRoom, null, 10, null))
                assertFails {
                    client.searchRoomMembers(privateRoom, null, 10, null).getOrThrow()
                }
                assertFails {
                    client.joinRoom(privateRoom).getOrThrow()
                }
                DatabaseFactory.createMemberJoin(MemberJoin(it.uid, privateRoom, ObjectType.ROOM, now())).getOrThrow()
                assertListSize(2, client.searchRoomMembers(privateRoom, null, 10, null))
            }
        }
    }
}
