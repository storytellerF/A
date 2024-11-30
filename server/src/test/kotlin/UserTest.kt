import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.a.client_lib.getData
import com.storyteller_f.a.client_lib.getUserInfo
import com.storyteller_f.a.client_lib.getUserInfoByAid
import com.storyteller_f.a.client_lib.signIn
import com.storyteller_f.a.client_lib.updateUserInfo
import com.storyteller_f.shared.finalData
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.signature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UserTest {
    @Test
    fun `test get user`() = test { client ->
        session(client)
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

    @Test
    fun `test login`() = test { client ->
        val (p, _, add) = session(client)
        val oldUid = LoginViewModel.user.value!!.id
        LoginViewModel.logout()
        val data = finalData(client.getData())
        val signature = signature(p, data)
        val userInfo = client.signIn(add, signature)
        assertEquals(oldUid, userInfo.id)
    }
}
