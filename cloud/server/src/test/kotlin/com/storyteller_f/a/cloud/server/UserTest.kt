package com.storyteller_f.a.cloud.server

import com.storyteller_f.a.client.core.UploadData
import com.storyteller_f.a.client.core.addChildAccount
import com.storyteller_f.a.client.core.getChildAccounts
import com.storyteller_f.a.client.core.getUserInfo
import com.storyteller_f.a.client.core.updateUserInfo
import com.storyteller_f.a.client.core.upload
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.UpdateUserBody
import com.storyteller_f.shared.type.ObjectType
import io.ktor.http.*
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
            val updateRow = updateUserInfo(
                UpdateUserBody(aid = "aid")
            ).getOrThrow()
            assertEquals(updateRow.aid, "aid")
            updateUserInfo(UpdateUserBody(nickname = "test")).getOrThrow()
            assertEquals("test", getUserInfo(it.uid).getOrThrow().nickname)
        }
    }

    @Test
    fun `test update user avatar`() = test {
        attachSession {
            val stream = ClassLoader.getSystemResourceAsStream("avatar1.png")!!
            val bytes = stream.readBytes()
            val info =
                upload(
                    ObjectTuple(it.uid, ObjectType.USER),
                    getUploadDataFromBytes(bytes)
                )
                    .getOrThrow().data.first()
            assertEquals(
                "avatar1.png",
                updateUserInfo(UpdateUserBody(avatar = info.id)).getOrThrow().avatar!!.name
            )
        }
    }

    @Test
    fun `test login`() = test {
        val session = attachSession {
        }
        loginSession(session) {
            assertEquals(session.uid, it.uid)
        }
    }

    @Test
    fun `test add alternative account`() = test {
        attachSession {
            val childAccountInfo = addChildAccount().getOrThrow()
            childAccountInfo
            assertEquals(it.uid, childAccountInfo.hostId)
            val response = getChildAccounts(null, 10).getOrThrow()
            assertEquals(1, response.pagination?.total)
            assertEquals(childAccountInfo.id, response.data.first().id)
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
