package com.storyteller_f.a.cloud.server

import com.storyteller_f.a.api.NewCommunity
import com.storyteller_f.a.api.NewFavorite
import com.storyteller_f.a.api.NewRoom
import com.storyteller_f.a.api.NewSubscription
import com.storyteller_f.a.api.PaginationQuery
import com.storyteller_f.a.client.core.UploadData
import com.storyteller_f.a.client.core.addChildAccount
import com.storyteller_f.a.client.core.addFavorite
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
import com.storyteller_f.a.client.core.removeFavorite
import com.storyteller_f.a.client.core.removeSubscription
import com.storyteller_f.a.client.core.sendMessage
import com.storyteller_f.a.client.core.updateUserInfo
import com.storyteller_f.a.client.core.upload
import com.storyteller_f.shared.obj.UpdateUserBody
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import io.ktor.http.ContentType
import kotlinx.io.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
        suspend fun TestMate.testChildAccount(
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
                childAccountInfo
            }

            // Now log in as the child account
            val childAccountInfo = outerTuple.custom
            val hostAuthKey = outerTuple.authKey
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
            val childAuthKey = if (childAccountInfo.algoType == com.storyteller_f.shared.model.AlgoType.DILITHIUM) {
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
            val receivedFrame = mutableListOf<com.storyteller_f.shared.obj.RoomFrame>()
            loginSession(SessionOuterTuple(childAuthKey, childAccountInfo.id, Unit), onReceive = { roomFrame, _, _ ->
                receivedFrame.add(roomFrame)
            }) {
                // create topic in user
                createTopic(ObjectType.USER, it.uid, "user topic $index").getOrThrow()

                // create topic in community
                val community = createCommunity(NewCommunity("test com $index", "test_com_$index")).getOrThrow()
                createTopic(ObjectType.COMMUNITY, community.id, "community topic $index").getOrThrow()

                // create topic in community room
                val comRoomId = createRoom(
                    NewRoom(name = "com room $index", aid = "test_com_room_$index", communityId = community.id)
                ).getOrThrow().id
                val roomInfo = getRoomInfo(comRoomId).getOrThrow()
                val keys1 = getRoomMembersPublicKeys(
                    comRoomId,
                    com.storyteller_f.a.api.PaginationQuery(null, size = 10)
                ).getOrThrow().data
                createTopicInRoomAndWait(receivedFrame) {
                    sendMessage(
                        com.storyteller_f.shared.obj.ObjectTuple(roomInfo.id, ObjectType.ROOM),
                        roomInfo.isPrivate,
                        "com room topic $index",
                        keys1
                    )
                }

                // create topic in private room
                val privateRoomId = createRoom(
                    NewRoom(name = "prv room $index", aid = "test_prv_room_$index")
                ).getOrThrow().id
                val roomInfo2 = getRoomInfo(privateRoomId).getOrThrow()
                val keys2 = getRoomMembersPublicKeys(
                    privateRoomId,
                    com.storyteller_f.a.api.PaginationQuery(null, size = 10)
                ).getOrThrow().data
                createTopicInRoomAndWait(receivedFrame) {
                    sendMessage(
                        com.storyteller_f.shared.obj.ObjectTuple(roomInfo2.id, ObjectType.ROOM),
                        roomInfo2.isPrivate,
                        "private room topic $index",
                        keys2
                    )
                }
            }
        }
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
}

fun getUploadDataFromBytes(bytes: ByteArray) = UploadData(
    bytes.size.toLong(),
    "avatar1.png",
    ContentType.parse("image/png")
) {
    Buffer().apply {
        write(bytes)
    }
}
