import com.storyteller_f.a.client_lib.getUserInfo
import com.storyteller_f.a.client_lib.getUserInfoByAid
import com.storyteller_f.a.client_lib.updateUserInfo
import com.storyteller_f.a.client_lib.upload
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.ObjectType
import io.ktor.http.*
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
                UserInfo.EMPTY.copy(aid = "newaid")
            ).getOrThrow()
            assertEquals(updateRow.aid, "newaid")
            assertEquals(uid, client.getUserInfoByAid("newaid").getOrThrow().id)
            client.updateUserInfo(UserInfo.EMPTY.copy(nickname = "test")).getOrThrow()
            assertEquals("test", client.getUserInfo(uid).getOrThrow().nickname)
            // 更新头像
            val stream = ClassLoader.getSystemClassLoader().getResourceAsStream("avatar1.png")!!
            val info =
                client.upload(stream.readBytes(), "avatar1.png", uid, ObjectType.USER, ContentType.parse("image/png"))
                    .getOrThrow().data.first()
            assertEquals(
                "avatar1.png",
                client.updateUserInfo(UserInfo.EMPTY.copy(avatar = info)).getOrThrow().avatar!!.item.noPrefixName
            )
        }
    }

    @Test
    fun `test login`() = test { client, _ ->
        val session = attachSession(client) {
        }
        loginSession(client, session) {
            assertEquals(session.id, it.uid)
        }
    }
}
