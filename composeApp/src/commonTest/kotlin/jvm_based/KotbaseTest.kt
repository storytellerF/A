package jvm_based

import com.storyteller_f.a.app.common.SectionLoadParams
import com.storyteller_f.a.client_lib.KotbaseDatabaseSource
import com.storyteller_f.a.client_lib.createKotbase
import com.storyteller_f.a.client_lib.save
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals

class KotbaseTest : UsingContextTest() {
    @Test
    fun testSectionLoadParams() {
        val params = SectionLoadParams<PrimaryKey>(0, 1)

        val collection =  KotbaseDatabaseSource(createKotbase()).getCollection("topics_keys", "")
        collection.save("1", Json.encodeToString(params))
        val params1 = collection.getDocument("1", serializer<SectionLoadParams<PrimaryKey>>())
        assertEquals(params, params1)
    }
}