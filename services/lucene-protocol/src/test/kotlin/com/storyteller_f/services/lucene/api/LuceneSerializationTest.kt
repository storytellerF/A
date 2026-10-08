/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.services.lucene.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class LuceneSerializationTest {
    @Test
    fun `fields retain types settings and repeated names`() {
        val document =
            RpcLuceneDocument(
                listOf(
                    RpcLuceneField("title", RpcLuceneValue.Text("apple"), RpcLuceneIndex.TEXT, stored = false),
                    RpcLuceneField("tag", RpcLuceneValue.Text("one"), RpcLuceneIndex.EXACT),
                    RpcLuceneField("tag", RpcLuceneValue.Text("two"), RpcLuceneIndex.EXACT),
                    RpcLuceneField(
                        "quantity",
                        RpcLuceneValue.LongNumber(Long.MAX_VALUE),
                        docValues = RpcLuceneDocValues.NUMERIC,
                    ),
                ),
            )
        val encoded = Json.encodeToString(RpcLuceneDocument.serializer(), document)
        assertEquals(document, Json.decodeFromString(RpcLuceneDocument.serializer(), encoded))
    }

    @Test
    fun `stored results retain strings longs and lists`() {
        val result =
            RpcLuceneResult(
                listOf(
                    RpcLuceneStoredDocument(
                        mapOf(
                            "tag" to listOf(RpcLuceneValue.Text("123"), RpcLuceneValue.Text("456")),
                            "quantity" to listOf(RpcLuceneValue.LongNumber(Long.MIN_VALUE)),
                        ),
                    ),
                ),
                1,
            )
        val encoded = Json.encodeToString(RpcLuceneResult.serializer(), result)
        assertEquals(result, Json.decodeFromString(RpcLuceneResult.serializer(), encoded))
    }

    @Test
    fun `query retains explicit sort fields and types`() {
        val query =
            RpcLuceneQuery(
                mustKeyword = mapOf("tag" to "one"),
                sort = listOf(RpcLuceneSort("quantity", RpcLuceneSortType.LONG, descending = true)),
            )
        val encoded = Json.encodeToString(RpcLuceneQuery.serializer(), query)
        assertEquals(query, Json.decodeFromString(RpcLuceneQuery.serializer(), encoded))
    }
}
