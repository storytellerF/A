import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.client_lib.joinCommunity
import com.storyteller_f.a.client_lib.joinRoom
import com.storyteller_f.a.client_lib.searchRoomMembers
import com.storyteller_f.a.client_lib.searchRooms
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.type.DEFAULT_PRIMARY_KEY
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.Community
import com.storyteller_f.tables.MemberJoin
import com.storyteller_f.tables.Room
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class RoomTest {
    @Test
    fun `test room search`() {
        test { client ->
            val community1 = SnowflakeFactory.nextId()
            DatabaseFactory.dbQuery {
                Community.new(Community("c1", "name1", null, DEFAULT_PRIMARY_KEY, null, community1, now()))
            }.getOrThrow()
            val room1 = SnowflakeFactory.nextId()
            val room2 = SnowflakeFactory.nextId()
            val room3 = SnowflakeFactory.nextId()
            createRooms(community1, room1, room2, room3)
            attachSession(client) {
                assertFails {
                    client.joinRoom(room1)
                }
                client.joinCommunity(community1)
                client.joinRoom(room1)
                assertEquals(1, client.searchRooms(10, null, JoinStatusSearch.JOINED, null, null).data.size)
                assertEquals(1, client.searchRooms(10, null, JoinStatusSearch.NOT_JOINED, null, null).data.size)
                assertEquals(2, client.searchRooms(10, null, JoinStatusSearch.UNSPECIFIED, null, null).data.size)
                assertEquals(1, client.searchRooms(10, null, JoinStatusSearch.UNSPECIFIED, "name2", null).data.size)
                DatabaseFactory.dbQuery {
                    MemberJoin.new(MemberJoin(it.data4, room3, ObjectType.ROOM, now()))
                }
                assertEquals(2, client.searchRooms(10, null, JoinStatusSearch.JOINED, null, null).data.size)
                assertEquals(2, client.searchRooms(10, null, JoinStatusSearch.UNSPECIFIED, null, community1).data.size)
            }
        }
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
            val community1 = SnowflakeFactory.nextId()
            DatabaseFactory.dbQuery {
                Community.new(Community("c1", "name1", null, DEFAULT_PRIMARY_KEY, null, community1, now()))
            }.getOrThrow()
            val room1 = SnowflakeFactory.nextId()
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
            val room2 = SnowflakeFactory.nextId()
            createRoom(
                Room(
                    "r2",
                    "name2",
                    icon = null,
                    creator = DEFAULT_PRIMARY_KEY,
                    communityId = null,
                    id = room2,
                    createdTime = now()
                )
            ).getOrThrow()
            attachSession(client) {
                client.joinCommunity(community1)
                client.joinRoom(room1)
            }
            attachSession(client) {
                assertEquals(1, client.searchRoomMembers(room1, null, 10, null).data.size)
                client.joinCommunity(community1)
                client.joinRoom(room1)
                assertEquals(2, client.searchRoomMembers(room1, null, 10, null).data.size)
                assertFails {
                    client.searchRoomMembers(room2, null, 10, null)
                }
                assertFails {
                    client.joinRoom(room2)
                }
                DatabaseFactory.dbQuery {
                    MemberJoin.new(MemberJoin(it.data4, room2, ObjectType.ROOM, now()))
                }
                assertEquals(1, client.searchRoomMembers(room2, null, 10, null).data.size)
            }
        }
    }
}

private suspend fun createRoom(room4: Room): Result<Boolean> = DatabaseFactory.dbQuery {
    Room.new(room4)
}
