import com.storyteller_f.a.client_lib.UploadData
import com.storyteller_f.a.client_lib.getUserInfo
import com.storyteller_f.a.client_lib.getUserInfoByAid
import com.storyteller_f.a.client_lib.updateUserInfo
import com.storyteller_f.a.client_lib.upload
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
            val updateRow = updateUserInfo(
                UpdateUserBody(aid = "newaid")
            ).getOrThrow()
            assertEquals(updateRow.aid, "newaid")
            assertEquals(uid, getUserInfoByAid("newaid").getOrThrow().id)
            updateUserInfo(UpdateUserBody(nickname = "test")).getOrThrow()
            assertEquals("test", getUserInfo(uid).getOrThrow().nickname)
            // 更新头像
            val stream = ClassLoader.getSystemClassLoader().getResourceAsStream("avatar1.png")!!
            val bytes = stream.readBytes()
            val info =
                upload(
                    ObjectTuple(it.uid, ObjectType.USER),
                    UploadData(
                        bytes.size.toLong(),
                        "avatar1.png",
                        ContentType.parse("image/png")
                    )
                ) {
                    Buffer().apply {
                        write(bytes)
                    }
                }
                    .getOrThrow().data.first()
            assertEquals(
                "avatar1.png",
                updateUserInfo(UpdateUserBody(avatar = info.newFullName)).getOrThrow().avatar!!.name
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
}
