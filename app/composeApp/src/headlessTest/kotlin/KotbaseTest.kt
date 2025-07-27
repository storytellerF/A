import com.storyteller_f.a.app.compose_app.common.SectionLoadParams
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.storage.DocumentSourceOrder
import com.storyteller_f.storage.createKotbaseStorageSource
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

class KotbaseTest : UsingContextTest() {
    @Test
    fun testSectionLoadParams() {
        val params = SectionLoadParams(0, "1")

        val collection = createKotbaseStorageSource(null, Json).getCollection("topics_keys", SectionLoadParams::class)
        collection.saveDocument("1", params)
        val params1 = collection.getDocument("1")
        assertEquals(params, params1)
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `test kotbase order`() = runTest {
        val collection = createKotbaseStorageSource(null, Json).getCollection("communities_test", CommunityInfo::class)
        collection.saveDocument("1", CommunityInfo.EMPTY.copy(hasPoster = true, id = 1))
        collection.saveDocument("2", CommunityInfo.EMPTY.copy(hasPoster = false, id = 2))
        collection.saveDocument("3", CommunityInfo.EMPTY.copy(hasPoster = true, id = 3))
        collection.saveDocument("4", CommunityInfo.EMPTY.copy(hasPoster = false, id = 4))
        val deferred = collection.observeData(listOf(DocumentSourceOrder.Desc("hasPoster"), DocumentSourceOrder.Desc("id")), 10) {

        }.deferred
        while (!deferred.isCompleted) {
            executeIfNeed()
        }
        val communityInfos = deferred.await()
        communityInfos.forEach {
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