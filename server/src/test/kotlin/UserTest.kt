import com.storyteller_f.a.client_lib.getUserInfo
import com.storyteller_f.a.client_lib.getUserInfoByAid
import com.storyteller_f.a.client_lib.updateUserInfo
import com.storyteller_f.shared.model.UserInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UserTest {
    @Test
    fun `test get user`() = test { client ->
        attachSession(client) {
            val uid = it.data4
            assertNotNull(uid)
            val aid = client.getUserInfo(uid).getOrThrow().aid
            assertNull(aid)
            val updateRow = client.updateUserInfo(
                UserInfo.EMPTY.copy(aid = "newaid")
            ).getOrThrow()
            assertEquals(updateRow.aid, "newaid")
            val user = client.getUserInfoByAid("newaid").getOrThrow()
            assertEquals(uid, user.id)
        }
    }

    @Test
    fun `test login`() = test { client ->
        val session = attachSession(client) {
        }
        loginSession(client, session) {
            assertEquals(session.data4, it.data4)
        }
    }
}
