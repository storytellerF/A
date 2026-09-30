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
    suspend fun get(index: String, ids: List<Long>): List<String?>
    suspend fun clean(index: String)
    suspend fun delete(index: String, query: RpcLuceneQuery)
    suspend fun search(index: String, query: RpcLuceneQuery): RpcLuceneResult
}

@Serializable
data class RpcLuceneDocument(
    val id: Long,
    val payload: String,
    val textFields: Map<String, String> = emptyMap(),
    val keywordFields: Map<String, String> = emptyMap(),
    val longFields: Map<String, Long> = emptyMap(),
)

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
    val sortByIdDescending: Boolean = false,
)

@Serializable
data class RpcLuceneResult(val payloads: List<String>, val total: Long)
