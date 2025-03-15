package jvm_based

import com.storyteller_f.a.app.common.SectionLoadParams
import com.storyteller_f.a.app.common.getOrCreateCollection
import com.storyteller_f.shared.type.PrimaryKey
import kotbase.MutableDocument
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class KotbaseTest : UsingContextTest() {
    @Test
    fun testSectionLoadParams() {
        val params = SectionLoadParams<PrimaryKey>(0, 1)
        val collection = getOrCreateCollection("topics_keys")
        collection.save(MutableDocument("1", Json.encodeToString(params)))
        val params1 = collection.getDocument("1")?.toJSON()?.let {
            Json.decodeFromString<SectionLoadParams<PrimaryKey>>(it)
        }
        assertEquals(params, params1)
    }
}