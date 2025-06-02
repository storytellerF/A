package com.storyteller_f.storage

import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

interface DatabaseObserverToken<T> {
    val task: CompletableDeferred<List<T>>

    fun remove()
}

sealed interface DatabaseOrder {
    data class Asc(val field: String) : DatabaseOrder
    data class Desc(val field: String) : DatabaseOrder
}

interface DatabaseCollection {
    fun saveDocument(id: String, string: String)
    fun <T : Any> observe(serializer: KSerializer<T>, expression: DatabaseExpression): Flow<T?>
    fun <T> getDocument(expression: DatabaseExpression, serializer: KSerializer<T>): T?
    fun <T> getDocument(key: String, serializer: KSerializer<T>): T?
    fun exists(expression: DatabaseExpression): Boolean
    fun deleteDocument(key: String)
    fun <T> observeList(
        expression: DatabaseExpression?,
        size: Int,
        orders: List<DatabaseOrder>,
        serializer: KSerializer<T>,
        invalidate: () -> Unit
    ): DatabaseObserverToken<T>

    fun <T> getDocument(id: PrimaryKey, serializer: KSerializer<T>): T? {
        return getDocument(id.toString(), serializer)
    }

    fun deleteDocument(id: PrimaryKey) {
        deleteDocument(id.toString())
    }

    fun <T> update(id: PrimaryKey, serializer: KSerializer<T>, block: (T) -> T) {
        val document = getDocument(id, serializer) ?: return
        val value = block(document)
        saveDocument(id.toString(), Json.encodeToString(serializer, value))
    }
}

sealed interface DatabaseExpression {
    data class IdEq(val field: String, val value: PrimaryKey) : DatabaseExpression
    data class StrEq(val field: String, val value: String) : DatabaseExpression
    data class Less(val field: String, val value: PrimaryKey) : DatabaseExpression
}

interface DatabaseSource {
    fun getCollection(name: String, scope: String?): DatabaseCollection
    fun getCollectionByPrefix(prefix: String, scope: String?): List<DatabaseCollection>
    suspend fun clearCollection(collectionName: String)

    companion object {
        val EMPTY = object : DatabaseSource {
            override fun getCollection(name: String, scope: String?): DatabaseCollection {
                TODO("Not yet implemented")
            }

            override fun getCollectionByPrefix(prefix: String, scope: String?): List<DatabaseCollection> {
                TODO("Not yet implemented")
            }

            override suspend fun clearCollection(collectionName: String) {
                TODO("Not yet implemented")
            }
        }
    }
}

fun DatabaseCollection.save(key: String, data: String) {
    saveDocument(key, data)
}

fun DatabaseCollection.save(key: PrimaryKey, data: String) {
    saveDocument(key.toString(), data)
}

inline fun <reified T : Any> DatabaseCollection.save(key: PrimaryKey, data: T) {
    saveDocument(key.toString(), Json.encodeToString(data))
}

inline fun <reified T : Any> DatabaseCollection.save(key: String, data: T) {
    saveDocument(key, Json.encodeToString(data))
}
