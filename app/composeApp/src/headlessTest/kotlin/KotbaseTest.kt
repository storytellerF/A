import com.storyteller_f.a.app.common.SectionLoadParams
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.storage.StorageOrder
import com.storyteller_f.storage.createKotbaseDatabaseSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KotbaseTest : UsingContextTest() {
    @Test
    fun testSectionLoadParams() {
        val params = SectionLoadParams(0, "1")

        val collection = createKotbaseDatabaseSource(null).getCollection("topics_keys", SectionLoadParams::class)
        collection.saveDocument("1", params)
        val params1 = collection.getDocument("1")
        assertEquals(params, params1)
    }

    @Test
    fun `test kotbase order`() = runTest {
        val collection = createKotbaseDatabaseSource(null).getCollection("communities_test", CommunityInfo::class)
        collection.saveDocument("1", CommunityInfo.EMPTY.copy(hasPoster = true, id = 1))
        collection.saveDocument("2", CommunityInfo.EMPTY.copy(hasPoster = false, id = 2))
        collection.saveDocument("3", CommunityInfo.EMPTY.copy(hasPoster = true, id = 3))
        collection.saveDocument("4", CommunityInfo.EMPTY.copy(hasPoster = false, id = 4))
        val task = collection.observeData(listOf(StorageOrder.Desc("hasPoster"), StorageOrder.Desc("id")), 10) {

        }.task
        while (!task.isCompleted) {
            executeIfNeed()
        }
        val communityInfos = task.await()
        communityInfos.forEach {
            println("${it.hasPoster} ${it.id}")
        }
    }

}