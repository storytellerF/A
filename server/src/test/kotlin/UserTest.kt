import com.storyteller_f.a.client_lib.LoginViewModel
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
        session(client) {
            val uid = LoginViewModel.user.value?.id
            assertNotNull(uid)
            val aid = client.getUserInfo(uid).aid
            assertNull(aid)
            val updateRow = client.updateUserInfo(
                UserInfo.EMPTY.copy(aid = "newaid")
            )
            assertEquals(1, updateRow)
            val user = client.getUserInfoByAid("newaid")
            assertEquals(uid, user.id)
        }
    }
}
