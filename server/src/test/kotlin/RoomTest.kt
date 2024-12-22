import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.client_lib.joinCommunity
import com.storyteller_f.a.client_lib.joinRoom
import com.storyteller_f.a.client_lib.searchRoomMembers
import com.storyteller_f.a.client_lib.searchRooms
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.type.DEFAULT_PRIMARY_KEY
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
    fun `test room search`() {
        test { client ->
            val community1 = SnowflakeFactory.nextId()
            createCommunity(Community("c1", "name1", null, DEFAULT_PRIMARY_KEY, null, community1, now())).getOrThrow()
            val room1 = SnowflakeFactory.nextId()
            val room2 = SnowflakeFactory.nextId()
            val room3 = SnowflakeFactory.nextId()
            createRooms(community1, room1, room2, room3)
            attachSession(client) {
                assertFails {
                    client.joinRoom(room1).getOrThrow()
                }
                client.joinCommunity(community1)
                client.joinRoom(room1)
                testSearchRoom(1, 10, null, JoinStatusSearch.JOINED, null, null, client)
                testSearchRoom(1, 10, null, JoinStatusSearch.NOT_JOINED, null, null, client)
                testSearchRoom(2, 10, null, JoinStatusSearch.UNSPECIFIED, null, null, client)
                testSearchRoom(1, 10, null, JoinStatusSearch.UNSPECIFIED, "name2", null, client)
                val join = MemberJoin(it.data4, room3, ObjectType.ROOM, now())
                createMemberJoin(join).getOrThrow()
                testSearchRoom(2, 10, null, JoinStatusSearch.JOINED, null, null, client)
                testSearchRoom(2, 10, null, JoinStatusSearch.UNSPECIFIED, null, community1, client)
            }
        }
    }

    suspend fun testSearchRoom(
        expected: Int,
        size: Int,
        nextRoomId: PrimaryKey?,
        joinStatusSearch: JoinStatusSearch,
        word: String?,
        communityId: PrimaryKey?,
        client: HttpClient
    ) {
        assertEquals(
            expected,
            client.searchRooms(size, nextRoomId, joinStatusSearch, word, communityId).getOrThrow().data.size
        )
    }

    private suspend fun createRooms(
        community1: PrimaryKey,
        room1: PrimaryKey,
        room2: PrimaryKey,
        room3: PrimaryKey
    ) {
        createRoom(
            Room(
                "r1",
                "name1",
                null,
                creator = DEFAULT_PRIMARY_KEY,
                communityId = community1,
                id = room1,
                createdTime = now()
            )
        ).getOrThrow()
        createRoom(
            Room(
                "r2",
                "name2",
                icon = null,
                creator = DEFAULT_PRIMARY_KEY,
                communityId = community1,
                id = room2,
                createdTime = now()
            )
        ).getOrThrow()
        createRoom(
            Room(
                "r3",
                "name3",
                null,
                creator = DEFAULT_PRIMARY_KEY,
                communityId = null,
                id = room3,
                createdTime = now()
            )
        ).getOrThrow()
    }

    @Test
    fun `test search room members`() {
        test { client ->
            val communityId = SnowflakeFactory.nextId()
            createCommunity(Community("c1", "name1", null, DEFAULT_PRIMARY_KEY, null, communityId, now())).getOrThrow()
            val publicRoom = SnowflakeFactory.nextId()
            createRoom(
                Room(
                    "r1",
                    "name1",
                    null,
                    creator = DEFAULT_PRIMARY_KEY,
                    communityId = communityId,
                    id = publicRoom,
                    createdTime = now()
                )
            ).getOrThrow()
            val privateRoom = SnowflakeFactory.nextId()
            createRoom(
                Room(
                    "r2",
                    "name2",
                    icon = null,
                    creator = DEFAULT_PRIMARY_KEY,
                    communityId = null,
                    id = privateRoom,
                    createdTime = now()
                )
            ).getOrThrow()
            attachSession(client) {
                client.joinCommunity(communityId)
                client.joinRoom(publicRoom)
            }
            attachSession(client) {
                assertEquals(1, client.searchRoomMembers(publicRoom, null, 10, null).getOrThrow().data.size)
                client.joinCommunity(communityId)
                client.joinRoom(publicRoom)
                assertEquals(2, client.searchRoomMembers(publicRoom, null, 10, null).getOrThrow().data.size)
                assertFails {
                    client.searchRoomMembers(privateRoom, null, 10, null).getOrThrow()
                }
                assertFails {
                    client.joinRoom(privateRoom).getOrThrow()
                }
                createMemberJoin(MemberJoin(it.data4, privateRoom, ObjectType.ROOM, now())).getOrThrow()
                assertEquals(1, client.searchRoomMembers(privateRoom, null, 10, null).getOrThrow().data.size)
            }
        }
    }
}
