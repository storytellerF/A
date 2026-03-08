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
import com.storyteller_f.a.client.core.getSubscriptions
import com.storyteller_f.a.client.core.getTopicInfo
import com.storyteller_f.a.client.core.getUserInfo
import com.storyteller_f.a.client.core.removeFavorite
import com.storyteller_f.a.client.core.removeSubscription
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
        attachSession {
            val childAccountInfo = addChildAccount().getOrThrow()
            assertEquals(it.uid, childAccountInfo.hostId)
            val response = getChildAccounts(null, 10).getOrThrow()
            assertEquals(1, response.pagination?.total)
            assertEquals(childAccountInfo.id, response.data.first().id)
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
