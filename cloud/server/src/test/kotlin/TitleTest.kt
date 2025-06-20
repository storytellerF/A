import com.storyteller_f.a.client_lib.createCommunity
import com.storyteller_f.a.client_lib.createTitle
import com.storyteller_f.a.client_lib.userTitles
import com.storyteller_f.shared.model.TitleSearchType
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.obj.NewCommunity
import com.storyteller_f.shared.obj.NewTitle
import com.storyteller_f.shared.type.ObjectType
import kotlin.test.Test

class TitleTest {
    @Test
    fun `test title`() = test {
        attachSession {
            val c = createCommunity(NewCommunity("c1", "c1")).getOrThrow()
            val cId = c.id
            assertListSize(0, userTitles(it.uid, null, 10, TitleSearchType.RECEIVER))
            createTitle(
                NewTitle("c KOL", TitleType.REGULAR, it.uid, cId, ObjectType.COMMUNITY, "hello")
            ).getOrThrow()
            assertListTotalSize(1, userTitles(it.uid, null, 10, TitleSearchType.RECEIVER))
            assertListSize(1, userTitles(it.uid, null, 10, TitleSearchType.RECEIVER))
            assertListSize(1, userTitles(it.uid, null, 10, TitleSearchType.CREATOR))
            assertListSize(1, userTitles(it.uid, null, 10, TitleSearchType.CREATOR, scopeId = cId))
            assertListSize(
                1,
                userTitles(it.uid, null, 10, TitleSearchType.CREATOR, scopeId = cId, type = TitleType.REGULAR)
            )
        }
    }
}
