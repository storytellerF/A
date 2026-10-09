/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.services.lucene.api

import kotlinx.rpc.annotations.Rpc
import kotlinx.serialization.Serializable

@Rpc
interface LuceneRpc {
    suspend fun health(): String
    suspend fun save(index: String, documents: List<RpcLuceneDocument>)
    suspend fun clean(index: String)
    suspend fun delete(index: String, query: RpcLuceneQuery)
    suspend fun search(index: String, query: RpcLuceneQuery): RpcLuceneResult
}

@Serializable
data class RpcLuceneDocument(val fields: List<RpcLuceneField> = emptyList())

@Serializable
sealed interface RpcLuceneValue {
    @Serializable
    data class Text(val value: String) : RpcLuceneValue

    @Serializable
    data class LongNumber(val value: Long) : RpcLuceneValue
}

@Serializable
enum class RpcLuceneIndex { NONE, TEXT, EXACT }

@Serializable
enum class RpcLuceneDocValues { NONE, NUMERIC, SORTED }

@Serializable
enum class RpcLuceneSortType { LONG, STRING }

@Serializable
data class RpcLuceneSort(val field: String, val type: RpcLuceneSortType, val descending: Boolean = false)

@Serializable
data class RpcLuceneField(
    val name: String,
    val value: RpcLuceneValue,
    val index: RpcLuceneIndex = RpcLuceneIndex.NONE,
    val stored: Boolean = true,
    val docValues: RpcLuceneDocValues = RpcLuceneDocValues.NONE,
)

@Serializable
data class RpcLuceneStoredDocument(val fields: Map<String, List<RpcLuceneValue>>)

@Serializable
data class RpcLuceneTextQuery(val word: String, val fields: List<String>)

@Serializable
data class RpcLuceneQuery(
    val mustLong: Map<String, Long> = emptyMap(),
    val mustNotLong: Map<String, Long> = emptyMap(),
    val mustLongSet: Map<String, List<Long>> = emptyMap(),
    val mustKeyword: Map<String, String> = emptyMap(),
    val text: List<RpcLuceneTextQuery> = emptyList(),
    val offset: Int = 0,
    val size: Int = 10,
    val sort: List<RpcLuceneSort> = emptyList(),
)

@Serializable
data class RpcLuceneResult(val documents: List<RpcLuceneStoredDocument>, val total: Long)
