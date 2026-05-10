package com.storyteller_f.a.cloud.server

import com.storyteller_f.a.api.NewCommunity
import com.storyteller_f.a.api.NewFavorite
import com.storyteller_f.a.api.NewRoom
import com.storyteller_f.a.api.NewSubscription
import com.storyteller_f.a.api.PaginationQuery
import com.storyteller_f.a.client.core.UploadData
import com.storyteller_f.a.client.core.addChildAccount
import com.storyteller_f.a.client.core.addFavorite
import com.storyteller_f.a.client.core.addReadLog
import com.storyteller_f.a.client.core.addSubscription
import com.storyteller_f.a.client.core.createCommunity
import com.storyteller_f.a.client.core.createRoom
import com.storyteller_f.a.client.core.createTopic
import com.storyteller_f.a.client.core.getChildAccounts
import com.storyteller_f.a.client.core.getCommunityInfo
import com.storyteller_f.a.client.core.getFavorites
import com.storyteller_f.a.client.core.getRoomInfo
import com.storyteller_f.a.client.core.getRoomMembersPublicKeys
import com.storyteller_f.a.client.core.getSubscriptions
import com.storyteller_f.a.client.core.getTopicInfo
import com.storyteller_f.a.client.core.getUserInfo
import com.storyteller_f.a.client.core.getUserOverview
import com.storyteller_f.a.client.core.hasUnreadRooms
import com.storyteller_f.a.client.core.removeFavorite
import com.storyteller_f.a.client.core.removeSubscription
import com.storyteller_f.a.client.core.sendMessage
import com.storyteller_f.a.client.core.updateUserInfo
import com.storyteller_f.a.client.core.upload
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.obj.UpdateUserBody
import com.storyteller_f.shared.obj.UpdateUserRead
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.sha256
import io.ktor.http.ContentType
import kotlinx.io.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserTest {
    @Test
    fun `test get user`() = test {
        attachSession {
            val uid = it.uid
            assertNotNull(uid)
            val aid = getUserInfo(uid).getOrThrow().aid
            assertNull(aid)
        }
    }

    @Test
    fun `test update user nickname and aid`() = test {
        attachSession {
            val updateRow = updateUserInfo(UpdateUserBody(aid = "aid")).getOrThrow()
            assertEquals(updateRow.aid, "aid")
            updateUserInfo(UpdateUserBody(nickname = "test")).getOrThrow()
            assertEquals("test", getUserInfo(it.uid).getOrThrow().nickname)
        }
    }

    @Test
    fun `test update user avatar`() = test {
        attachSession {
            val stream = ClassLoader.getSystemResourceAsStream("avatar1.png")!!.buffered()
            val bytes = stream.readBytes()
            val info = upload(it.uid ob ObjectType.USER, getUploadDataFromBytes(bytes)).getOrThrow().data.first()
            assertEquals("avatar1.png", updateUserInfo(UpdateUserBody(avatar = info.id)).getOrThrow().avatar!!.name)
        }
    }

    @Test
    fun `test login`() = test {
        val session = attachSession()
        loginSession(session) {
            assertEquals(session.uid, it.uid)
        }
    }

    @Test
    fun `test add alternative account`() = test {
        testChildAccount(1, com.storyteller_f.shared.model.AlgoType.P256, com.storyteller_f.shared.model.AlgoType.P256)
        testChildAccount(
            2,
            com.storyteller_f.shared.model.AlgoType.P256,
            com.storyteller_f.shared.model.AlgoType.DILITHIUM
        )
        testChildAccount(
            3,
            com.storyteller_f.shared.model.AlgoType.DILITHIUM,
            com.storyteller_f.shared.model.AlgoType.P256
        )
        testChildAccount(
            4,
            com.storyteller_f.shared.model.AlgoType.DILITHIUM,
            com.storyteller_f.shared.model.AlgoType.DILITHIUM
        )
    }

    private suspend fun TestMate.testChildAccount(
        index: Int,
        hostAlgo: com.storyteller_f.shared.model.AlgoType,
        childAlgo: com.storyteller_f.shared.model.AlgoType
    ) {
        val outerTuple = attachSession(hostAlgo) {
            val childAccountInfo = addChildAccount(childAlgo).getOrThrow()
            assertEquals(it.uid, childAccountInfo.hostId)
            val response = getChildAccounts(null, 10).getOrThrow()
            assertEquals(1, response.pagination?.total)
            assertEquals(childAccountInfo.id, response.data.first().id)
            assertFalse(response.data.first().hasUnreadRoomMessage)
            assertFalse(getUserOverview().getOrThrow().hasUnreadChildRoomMessage)
            childAccountInfo
        }

        val childAccountInfo = outerTuple.custom
        val childAuthKey = buildChildAuthKey(hostAlgo, outerTuple.authKey, childAccountInfo)
        val receivedFrame = mutableListOf<com.storyteller_f.shared.obj.RoomFrame>()
        loginSession(
            SessionOuterTuple(childAuthKey, childAccountInfo.id, Unit),
            onReceive = { roomFrame, _, _ -> receivedFrame.add(roomFrame) }
        ) {
            createTopic(ObjectType.USER, it.uid, "user topic $index").getOrThrow()

            val community = createCommunity(NewCommunity("test com $index", "test_com_$index")).getOrThrow()
            createTopic(ObjectType.COMMUNITY, community.id, "community topic $index").getOrThrow()

            createRoomTopic(
                roomName = "com room $index",
                roomAid = "test_com_room_$index",
                topicName = "com room topic $index",
                communityId = community.id,
                receivedFrame = receivedFrame
            )
            createRoomTopic(
                roomName = "prv room $index",
                roomAid = "test_prv_room_$index",
                topicName = "private room topic $index",
                communityId = null,
                receivedFrame = receivedFrame
            )
        }

        loginSession(SessionOuterTuple(outerTuple.authKey, outerTuple.uid, Unit)) {
            val response = getChildAccounts(null, 10).getOrThrow()
            assertTrue(response.data.first().hasUnreadRoomMessage)
            assertTrue(getUserOverview().getOrThrow().hasUnreadChildRoomMessage)
        }
    }

    private suspend fun buildChildAuthKey(
        hostAlgo: com.storyteller_f.shared.model.AlgoType,
        hostAuthKey: com.storyteller_f.a.client.core.AuthKey,
        childAccountInfo: com.storyteller_f.shared.model.ChildAccountInfo
    ): com.storyteller_f.a.client.core.AuthKey {
        val hostAddress = com.storyteller_f.shared.getAlgo(hostAlgo).calcAddress(
            if (hostAuthKey is com.storyteller_f.a.client.core.AuthKey.Dilithium) {
                hostAuthKey.derPublicKey
            } else {
                (hostAuthKey as com.storyteller_f.a.client.core.AuthKey.P256).derPublicKey
            }
        ).getOrThrow()

        val hostUserPass = com.storyteller_f.a.client.core.RawUserPass(
            com.storyteller_f.a.client.core.RawUserPassInfo(hostAddress, hostAuthKey)
        )
        val (decrypted, decryptedEnc) = hostUserPass.decryptChildAccount(
            childAccountInfo.encryptedPrivateKey,
            childAccountInfo.encryptedAesKey,
            childAccountInfo.algoType,
            childAccountInfo.encryptedEncryptionPrivateKey
        ).getOrThrow()

        val algoImpl = com.storyteller_f.shared.getAlgo(childAccountInfo.algoType)
        val pem = algoImpl.getPemPrivateKeyFromDer(decrypted).getOrThrow()
        val publicKey = algoImpl.getDerPublicKeyFromPrivateKey(pem).getOrThrow()
        return if (childAccountInfo.algoType == com.storyteller_f.shared.model.AlgoType.DILITHIUM) {
            com.storyteller_f.a.client.core.AuthKey.Dilithium(
                pemPrivateKey = pem,
                derPrivateKey = decrypted,
                derPublicKey = publicKey,
                pemEncryptionPrivateKey = "",
                derEncryptionPrivateKey = decryptedEnc ?: "",
                derEncryptionPublicKey = ""
            )
        } else {
            com.storyteller_f.a.client.core.AuthKey.P256(
                pemPrivateKey = pem,
                derPrivateKey = decrypted,
                derPublicKey = publicKey
            )
        }
    }

    private suspend fun com.storyteller_f.a.client.core.UserSessionManager.createRoomTopic(
        roomName: String,
        roomAid: String,
        topicName: String,
        communityId: Long?,
        receivedFrame: MutableList<com.storyteller_f.shared.obj.RoomFrame>
    ) {
        val roomId = createRoom(
            NewRoom(name = roomName, aid = roomAid, communityId = communityId)
        ).getOrThrow().id
        val roomInfo = getRoomInfo(roomId).getOrThrow()
        val keys = getRoomMembersPublicKeys(
            roomId,
            com.storyteller_f.a.api.PaginationQuery(null, size = 10)
        ).getOrThrow().data
        createTopicInRoomAndWait(receivedFrame) {
            sendMessage(
                com.storyteller_f.shared.obj.ObjectTuple(roomInfo.id, ObjectType.ROOM),
                roomInfo.isPrivate,
                topicName,
                keys
            )
        }
    }

    @Test
    fun `test add favorite`() = test {
        val outerTuple = attachSession {
            createTopic(ObjectType.USER, it.uid, "hello").getOrThrow()
        }
        attachSession {
            val topicId = outerTuple.custom.id
            addFavorite(NewFavorite(ObjectType.TOPIC, topicId)).getOrThrow()
            assertListTotalSize(1, getFavorites(PaginationQuery()))

            val topicInfo = getTopicInfo(topicId).getOrThrow()
            assertNotNull(topicInfo.favoriteId)

            removeFavorite(topicId, ObjectType.TOPIC).getOrThrow()

            assertListTotalSize(0, getFavorites(PaginationQuery()))
        }
    }

    @Test
    fun `test add subscription`() = test {
        val outerTuple = attachSession {
            createTopic(ObjectType.USER, it.uid, "hello").getOrThrow()
        }
        attachSession {
            val topicId = outerTuple.custom.id
            addSubscription(NewSubscription(topicId, ObjectType.TOPIC)).getOrThrow()
            assertListTotalSize(1, getSubscriptions(PaginationQuery()))

            val topicInfo = getTopicInfo(topicId).getOrThrow()
            assertNotNull(topicInfo.subscriptionId)

            removeSubscription(topicId, ObjectType.TOPIC).getOrThrow()

            assertListTotalSize(0, getSubscriptions(PaginationQuery()))
        }
    }

    @Test
    fun `test add user favorite`() = test {
        val targetUid = attachSession {}.uid
        attachSession {
            addFavorite(NewFavorite(ObjectType.USER, targetUid)).getOrThrow()
            assertListTotalSize(1, getFavorites(PaginationQuery()))

            val userInfo = getUserInfo(targetUid).getOrThrow()
            assertNotNull(userInfo.favoriteId)

            removeFavorite(targetUid, ObjectType.USER).getOrThrow()
            assertListTotalSize(0, getFavorites(PaginationQuery()))
        }
    }

    @Test
    fun `test add community favorite`() = test {
        val communityId = attachSession {
            createCommunity(NewCommunity("test", "test")).getOrThrow().id
        }.custom
        attachSession {
            addFavorite(NewFavorite(ObjectType.COMMUNITY, communityId)).getOrThrow()
            assertListTotalSize(1, getFavorites(PaginationQuery()))

            val communityInfo = getCommunityInfo(communityId).getOrThrow()
            assertNotNull(communityInfo.favoriteId)

            removeFavorite(communityId, ObjectType.COMMUNITY).getOrThrow()
            assertListTotalSize(0, getFavorites(PaginationQuery()))
        }
    }

    @Test
    fun `test add room favorite`() = test {
        val roomId = attachSession {
            createRoom(NewRoom("test", "test")).getOrThrow().id
        }.custom
        attachSession {
            addFavorite(NewFavorite(ObjectType.ROOM, roomId)).getOrThrow()
            assertListTotalSize(1, getFavorites(PaginationQuery()))

            val roomInfo = getRoomInfo(roomId).getOrThrow()
            assertNotNull(roomInfo.favoriteId)

            removeFavorite(roomId, ObjectType.ROOM).getOrThrow()
            assertListTotalSize(0, getFavorites(PaginationQuery()))
        }
    }

    @Test
    fun `test add user subscription`() = test {
        val targetUid = attachSession {}.uid
        attachSession {
            addSubscription(NewSubscription(targetUid, ObjectType.USER)).getOrThrow()
            assertListTotalSize(1, getSubscriptions(PaginationQuery()))

            val userInfo = getUserInfo(targetUid).getOrThrow()
            assertNotNull(userInfo.subscriptionId)

            removeSubscription(targetUid, ObjectType.USER).getOrThrow()
            assertListTotalSize(0, getSubscriptions(PaginationQuery()))
        }
    }

    @Test
    fun `test add community subscription`() = test {
        val communityId = attachSession {
            createCommunity(NewCommunity("test", "test")).getOrThrow().id
        }.custom
        attachSession {
            addSubscription(NewSubscription(communityId, ObjectType.COMMUNITY)).getOrThrow()
            assertListTotalSize(1, getSubscriptions(PaginationQuery()))

            val communityInfo = getCommunityInfo(communityId).getOrThrow()
            assertNotNull(communityInfo.subscriptionId)

            removeSubscription(communityId, ObjectType.COMMUNITY).getOrThrow()
            assertListTotalSize(0, getSubscriptions(PaginationQuery()))
        }
    }

    @Test
    fun `test add room subscription`() = test {
        val roomId = attachSession {
            createRoom(NewRoom("test", "test")).getOrThrow().id
        }.custom
        attachSession {
            addSubscription(NewSubscription(roomId, ObjectType.ROOM)).getOrThrow()
            assertListTotalSize(1, getSubscriptions(PaginationQuery()))

            val roomInfo = getRoomInfo(roomId).getOrThrow()
            assertNotNull(roomInfo.subscriptionId)

            removeSubscription(roomId, ObjectType.ROOM).getOrThrow()
            assertListTotalSize(0, getSubscriptions(PaginationQuery()))
        }
    }

    @Test
    fun `test has unread rooms`() = test {
        val receivedFrame = mutableListOf<RoomFrame>()
        attachSession(onReceive = { roomFrame, _, _ ->
            receivedFrame.add(roomFrame)
        }) {
            val roomInfo = createRoom(NewRoom("test", "test")).getOrThrow()
            val keys = getRoomMembersPublicKeys(roomInfo.id, PaginationQuery(null, size = 10)).getOrThrow().data
            createTopicInRoomAndWait(receivedFrame) {
                sendMessage(roomInfo.tuple(), true, "hello", keys)
            }
            val topicId = (receivedFrame.first() as RoomFrame.NewTopicInfo).topicInfo.id

            val response = hasUnreadRooms().getOrThrow()
            assertTrue(response.hasUnread)
            assertTrue(response.unreadCount > 0)

            addReadLog(UpdateUserRead(ObjectTuple(roomInfo.id, ObjectType.ROOM), topicId)).getOrThrow()

            val responseAfterRead = hasUnreadRooms().getOrThrow()
            assertFalse(responseAfterRead.hasUnread)
            assertEquals(0, responseAfterRead.unreadCount)
        }
    }
}

suspend fun getUploadDataFromBytes(bytes: ByteArray) = UploadData(
    bytes.size.toLong(),
    "avatar1.png",
    ContentType.parse("image/png"),
    sha256(
        Buffer().apply {
            write(bytes)
        }.peek()
    ),
) {
    Buffer().apply {
        write(bytes)
    }
}
