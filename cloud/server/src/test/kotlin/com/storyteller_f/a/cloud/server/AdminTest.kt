package com.storyteller_f.a.cloud.server

import com.storyteller_f.a.api.core.NewCommunity
import com.storyteller_f.a.api.core.NewRoom
import com.storyteller_f.a.api.core.NewUser
import com.storyteller_f.a.api.core.PaginationQuery
import com.storyteller_f.a.client.core.addUser
import com.storyteller_f.a.client.core.createCommunity
import com.storyteller_f.a.client.core.createRoom
import com.storyteller_f.a.client.core.createTopic
import com.storyteller_f.a.client.core.getAllUsers
import com.storyteller_f.a.client.core.getUserInfo
import com.storyteller_f.a.client.core.overview
import com.storyteller_f.a.client.core.upload
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminTest {
    @Test
    fun `test panel login`() = test {
        val panelTuple = attachPanelSession {
            assertListSize(0, getAllUsers(PaginationQuery()))
        }
        attachSession {
        }
        loginPanelSession(panelTuple) {
            assertListSize(1, getAllUsers(PaginationQuery()))
        }
    }

    @Test
    fun `test overview user count`() = test {
        val outerTuple = attachPanelSession {
            assertEquals(0, overview().getOrThrow().userCount)
        }
        attachSession {
        }
        loginPanelSession(outerTuple) {
            assertEquals(1, overview().getOrThrow().userCount)
        }
    }

    @Test
    fun `test overview community count`() = test {
        val outerTuple = attachPanelSession {
            assertEquals(0, overview().getOrThrow().communityCount)
        }
        attachSession {
            createCommunity(NewCommunity("test", "test")).getOrThrow()
        }
        loginPanelSession(outerTuple) {
            assertEquals(1, overview().getOrThrow().communityCount)
        }
    }

    @Test
    fun `test overview topic count`() = test {
        val outerTuple = attachPanelSession {
            assertEquals(0, overview().getOrThrow().topicCount)
        }
        attachSession {
            createTopic(ObjectType.USER, it.uid, "test").getOrThrow()
        }
        loginPanelSession(outerTuple) {
            assertEquals(1, overview().getOrThrow().topicCount)
        }
    }

    @Test
    fun `test overview private room count`() = test {
        val outerTuple = attachPanelSession {
            assertEquals(0, overview().getOrThrow().privateRoomCount)
        }
        attachSession {
            createRoom(NewRoom("test", "test")).getOrThrow()
        }
        loginPanelSession(outerTuple) {
            assertEquals(1, overview().getOrThrow().privateRoomCount)
        }
    }

    @Test
    fun `test overview public room count`() = test {
        val outerTuple = attachPanelSession {
            assertEquals(0, overview().getOrThrow().communityRoomCount)
        }
        attachSession {
            val communityInfo = createCommunity(NewCommunity("test", "test")).getOrThrow()
            val communityId = communityInfo.id
            createRoom(NewRoom("test", "test1", communityId = communityId)).getOrThrow()
        }
        loginPanelSession(outerTuple) {
            assertEquals(1, overview().getOrThrow().communityRoomCount)
        }
    }

    @Test
    fun `test overview file`() = test {
        val outerTuple = attachPanelSession {
            val panelOverview = overview().getOrThrow()
            assertEquals(0, panelOverview.fileCount)
            assertEquals(0, panelOverview.fileVolume)
        }
        attachSession {
            upload(it.uid ob ObjectType.USER, getUploadDataFromText("hello")).getOrThrow()
        }
        loginPanelSession(outerTuple) {
            val panelOverview = overview().getOrThrow()
            assertEquals(1, panelOverview.fileCount)
            assertEquals(5, panelOverview.fileVolume)
        }
    }

    @Test
    fun `test add user`() = test {
        val privateKey = getAlgo().run {
            generateECDSAPemPrivateKey().getOrThrow()
        }
        val publicKey = getAlgo().run {
            getDerPublicKeyFromPrivateKey(privateKey).getOrThrow()
        }
        val userInfo = attachPanelSession {
            addUser(NewUser(publicKey = publicKey)).getOrThrow()
        }.custom
        getAppSession(false, privateKey, { _, _, _ ->
        }) {
            val info = getUserInfo(it.uid).getOrThrow()
            assertEquals(info.id, userInfo.id)
        }
    }
}
