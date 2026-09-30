/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.cloud.lucene

import com.storyteller_f.a.backend.core.service.RpcLuceneDocument
import com.storyteller_f.a.backend.core.service.RpcLuceneQuery
import com.storyteller_f.a.backend.core.service.RpcLuceneTextQuery
import kotlinx.coroutines.runBlocking
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.LongField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LuceneIndexTest {
    @Test
    fun `legacy stored fields remain readable`() =
        runBlocking {
        val base = createTempDirectory("legacy-index-")
        FSDirectory.open(base.resolve("products")).use { directory ->
            IndexWriter(directory, IndexWriterConfig(StandardAnalyzer())).use { writer ->
                writer.addDocument(
                    Document().apply {
                        add(LongField("id1", 3, Field.Store.YES))
                        add(TextField("title", "legacy", Field.Store.YES))
                    },
                )
            }
        }
        val service = LuceneRpcImpl(base)
        assertEquals(listOf("""{"id":3,"title":"legacy"}"""), service.get("products", listOf(3)))
    }

    @Test
    fun `generic indexes filter opaque payloads`() =
        runBlocking {
        val service = LuceneRpcImpl(createTempDirectory("generic-index-"))
        service.save(
            "products",
            listOf(
                RpcLuceneDocument(
                    1,
                    "first",
                    textFields = mapOf("title" to "red apple"),
                    longFields =
                    mapOf(
                        "stock" to 2,
                    ),
                ),
                RpcLuceneDocument(
                    2,
                    "second",
                    textFields = mapOf("title" to "green apple"),
                    longFields = mapOf("stock" to 3),
                ),
            ),
        )
        val query =
            RpcLuceneQuery(
                mustLong = mapOf("stock" to 3),
                text = listOf(RpcLuceneTextQuery("apple", listOf("title"))),
            )
        assertEquals(listOf("second"), service.search("products", query).payloads)
        assertEquals(listOf("second", null, "first"), service.get("products", listOf(2, 99, 1)))
        assertEquals(0L, service.search("another", RpcLuceneQuery()).total)
        service.delete("products", query)
        assertEquals(listOf("first"), service.search("products", RpcLuceneQuery()).payloads)
        assertFailsWith<IllegalArgumentException> { service.clean("../outside") }
        Unit
    }
}
