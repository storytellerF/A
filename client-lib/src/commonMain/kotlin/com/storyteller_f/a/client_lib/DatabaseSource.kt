package com.storyteller_f.a.client_lib

import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

val json = Json {
    ignoreUnknownKeys = true
}

expect fun createPlatformDatabaseSource(): DatabaseSource

interface ObserverToken<T> {
    val task: CompletableDeferred<List<T>>

    fun remove()
}

sealed interface Order {
    data class NotNull(val field: String) : Order
    data class Asc(val field: String) : Order
    data class Desc(val field: String) : Order
}

interface DatabaseCollection {
    fun saveDocument(id: String, string: String)
    fun <T : Any> observe(serializer: KSerializer<T>, expression: Expression): Flow<T?>
    fun <T> getDocument(expression: Expression, serializer: KSerializer<T>): T?
    fun <T> getDocument(key: String, serializer: KSerializer<T>): T?
    fun exists(expression: Expression): Boolean
    fun deleteDocument(key: String)
    fun <T> observeList(
        expression: Expression?,
        size: Int,
        order: List<Order>,
        serializer: KSerializer<T>,
        invalidate: () -> Unit
    ): ObserverToken<T>

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

sealed interface Expression {
    data class IdEq(val field: String, val value: PrimaryKey) : Expression
    data class StrEq(val field: String, val value: String) : Expression
    data class Less(val field: String, val value: PrimaryKey) : Expression
}

interface DatabaseSource {
    fun getCollection(name: String): DatabaseCollection
    fun deleteCollection(collectionName: String)

    companion object {
        val EMPTY = object : DatabaseSource {
            override fun getCollection(name: String): DatabaseCollection {
                TODO("Not yet implemented")
            }

            override fun deleteCollection(collectionName: String) {
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
