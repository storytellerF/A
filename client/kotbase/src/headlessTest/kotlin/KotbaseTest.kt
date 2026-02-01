import androidx.paging.PagingSource
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.storage.CommunityCollection
import com.storyteller_f.storage.DocumentModelStorage
import com.storyteller_f.storage.RemoteKeys
import com.storyteller_f.storage.TopicCollection
import com.storyteller_f.storage.createKotbaseSource
import com.storyteller_f.storage.getName
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

class KotbaseTest : PlatformHeadlessTest() {
    @Test
    fun testSectionLoadParams() {
        val modelStorage = DocumentModelStorage(createKotbaseSource(null))
        runTest {
            modelStorage.remoteKey.saveNextRemoteKey(RemoteKeys(TopicCollection.Recommend.getName(), "1"))
            val remoteKeys = modelStorage.remoteKey.getNextRemoteKey(TopicCollection.Recommend.getName())
            assertEquals("1", remoteKeys?.key)
        }
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `test kotbase order`() = runTest {
        val collection = CommunityCollection.SearchCommunity(JoinStatusSearch.JOINED, "")
        val modelStorage = DocumentModelStorage(createKotbaseSource(null))
        modelStorage.community.saveToDefault(CommunityInfo.EMPTY.copy(hasPoster = true, id = 1))
        modelStorage.community.saveToDefault(CommunityInfo.EMPTY.copy(hasPoster = false, id = 2))
        modelStorage.community.saveToDefault(CommunityInfo.EMPTY.copy(hasPoster = true, id = 3))
        modelStorage.community.saveToDefault(CommunityInfo.EMPTY.copy(hasPoster = false, id = 4))
        val observeData = modelStorage.community.observeData(collection)
        val loadResult = observeData.load(PagingSource.LoadParams.Refresh(null, 10, false))
        assertTrue(loadResult is PagingSource.LoadResult.Page)
        loadResult.data.forEach {
            println("${it.hasPoster} ${it.id}")
        }
    }
}
