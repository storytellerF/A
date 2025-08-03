package com.storyteller_f.storage

import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

sealed interface DocumentSourceOrder {
    data class Asc(val field: String) : DocumentSourceOrder
    data class Desc(val field: String) : DocumentSourceOrder
}

interface DocumentCollection<T> {
    fun saveDocument(id: String, t: T)
    fun getDocument(expression: DocumentExpression): T?
    fun getDocument(id: String): T?
    fun exists(expression: DocumentExpression): Boolean
    fun deleteDocument(id: String)
    fun observeDatum(expression: DocumentExpression): Flow<T?>
    fun observeData(
        orders: List<DocumentSourceOrder>,
        size: Int,
        vararg expressions: DocumentExpression,
        invalidate: () -> Unit
    ): DocumentObservable<T>

    fun observeDatum(id: PrimaryKey): Flow<T?> {
        return observeDatum(DocumentExpression.IdEq("id", id))
    }
}

sealed interface DocumentExpression {
    data class IdEq(val field: String, val value: PrimaryKey) : DocumentExpression
    data class StrEq(val field: String, val value: String) : DocumentExpression
    data class Less(val field: String, val value: PrimaryKey) : DocumentExpression
    data class StrLess(val field: String, val value: String) : DocumentExpression
    data class IntLess(val field: String, val value: Int) : DocumentExpression
}

interface DocumentSource {
    fun <T : Any> getCollection(name: String, clazz: KClass<T>): DocumentCollection<T>
    fun <T : Any> getCollectionByPrefix(
        prefix: String,
        clazz: KClass<T>
    ): List<DocumentCollection<T>>

    suspend fun clearCollection(collectionName: String)

    companion object {
        val EMPTY = object : DocumentSource {
            override fun <T : Any> getCollection(
                name: String,
                clazz: KClass<T>
            ): DocumentCollection<T> {
                TODO("Not yet implemented")
            }

            override fun <T : Any> getCollectionByPrefix(
                prefix: String,
                clazz: KClass<T>
            ): List<DocumentCollection<T>> {
                TODO("Not yet implemented")
            }

            override suspend fun clearCollection(collectionName: String) {
                TODO("Not yet implemented")
            }
        }
    }
}

inline fun <reified T : Any> DocumentSource.getCollection(name: String): DocumentCollection<T> {
    return getCollection(name, T::class)
}

inline fun <reified T : Any> DocumentCollection<T>.save(key: PrimaryKey, data: T) {
    saveDocument(key.toString(), data)
}

inline fun <reified T : Any> DocumentCollection<T>.save(key: String?, data: T) {
    key ?: return
    saveDocument(key, data)
}

fun <T> DocumentCollection<T>.getDocument(id: PrimaryKey): T? {
    return getDocument(id.toString())
}

fun <T> DocumentCollection<T>.deleteDocument(id: PrimaryKey) {
    deleteDocument(id.toString())
}

fun <T> DocumentCollection<T>.update(id: PrimaryKey, block: (T) -> T) {
    val document = getDocument(id) ?: return
    val value = block(document)
    saveDocument(id.toString(), value)
}
