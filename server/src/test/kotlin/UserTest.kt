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
    fun `test get user`() = test { client, _ ->
        attachSession(client) {
            val uid = it.uid
            assertNotNull(uid)
            val aid = client.getUserInfo(uid).getOrThrow().aid
            assertNull(aid)
            val updateRow = client.updateUserInfo(
                UpdateUserBody(aid = "newaid")
            ).getOrThrow()
            assertEquals(updateRow.aid, "newaid")
            assertEquals(uid, client.getUserInfoByAid("newaid").getOrThrow().id)
            client.updateUserInfo(UpdateUserBody(nickname = "test")).getOrThrow()
            assertEquals("test", client.getUserInfo(uid).getOrThrow().nickname)
            // 更新头像
            val stream = ClassLoader.getSystemClassLoader().getResourceAsStream("avatar1.png")!!
            val bytes = stream.readBytes()
            val info =
                client.upload(
                    ObjectTuple(it.uid, ObjectType.USER),
                    bytes.size.toLong(),
                    "avatar1.png",
                    ContentType.parse("image/png")
                ) {
                    Buffer().apply {
                        write(bytes)
                    }
                }
                    .getOrThrow().data.first()
            assertEquals(
                "avatar1.png",
                client.updateUserInfo(UpdateUserBody(avatar = info.name)).getOrThrow().avatar!!.noPrefixName
            )
        }
    }

    @Test
    fun `test login`() = test { client, _ ->
        val session = attachSession(client) {
        }
        loginSession(client, session) {
            assertEquals(session.uid, it.uid)
        }
    }
}
