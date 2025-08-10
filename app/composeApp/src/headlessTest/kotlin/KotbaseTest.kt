import androidx.paging.PagingSource
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.storage.DocumentModelStorage
import com.storyteller_f.storage.ModelCollection
import com.storyteller_f.storage.RemoteKeys
import com.storyteller_f.storage.createKotbaseSource
import com.storyteller_f.storage.getName
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

class KotbaseTest : UsingContextTest() {
    @Test
    fun testSectionLoadParams() {
        val modelStorage = DocumentModelStorage(createKotbaseSource(null))
        runTest {
            modelStorage.remoteKeyStorage.saveNextRemoteKey(RemoteKeys(ModelCollection.Recommend.getName(), "1"))
            val remoteKeys =
                modelStorage.remoteKeyStorage.getNextRemoteKey(ModelCollection.Recommend)
            assertEquals("1", remoteKeys?.key)
        }
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `test kotbase order`() = runTest {
        val modelStorage = DocumentModelStorage(createKotbaseSource(null))
        modelStorage.communityStorage.save(ModelCollection.Communities, CommunityInfo.EMPTY.copy(hasPoster = true, id = 1))
        modelStorage.communityStorage.save(ModelCollection.Communities, CommunityInfo.EMPTY.copy(hasPoster = false, id = 2))
        modelStorage.communityStorage.save(ModelCollection.Communities, CommunityInfo.EMPTY.copy(hasPoster = true, id = 3))
        modelStorage.communityStorage.save(ModelCollection.Communities, CommunityInfo.EMPTY.copy(hasPoster = false, id = 4))
        val observeData = modelStorage.communityStorage.observeData(ModelCollection.Communities)
        val loadResult = observeData.load(PagingSource.LoadParams.Refresh(null, 10, false))
        assertTrue(loadResult is PagingSource.LoadResult.Page)
        loadResult.data.forEach {
            println("${it.hasPoster} ${it.id}")
        }
    }

    @Test
    fun `test activity`() {
        onActivity {
            println("activity")
        }
    }

}