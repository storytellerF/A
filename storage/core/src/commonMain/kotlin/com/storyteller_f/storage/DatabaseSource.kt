package com.storyteller_f.storage

import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

interface DatabaseObservable<T> {
    val task: CompletableDeferred<List<T>>

    fun remove()
}

sealed interface DatabaseOrder {
    data class Asc(val field: String) : DatabaseOrder
    data class Desc(val field: String) : DatabaseOrder
}

interface DatabaseCollection<T> {
    fun saveDocument(id: String, t: T)
    fun getDocument(expression: DatabaseExpression): T?
    fun getDocument(id: String): T?
    fun exists(expression: DatabaseExpression): Boolean
    fun deleteDocument(id: String)
    fun observeDatum(expression: DatabaseExpression): Flow<T?>
    fun observeData(
        orders: List<DatabaseOrder>,
        size: Int,
        expression: DatabaseExpression? = null,
        invalidate: () -> Unit
    ): DatabaseObservable<T>
}

sealed interface DatabaseExpression {
    data class IdEq(val field: String, val value: PrimaryKey) : DatabaseExpression
    data class StrEq(val field: String, val value: String) : DatabaseExpression
    data class Less(val field: String, val value: PrimaryKey) : DatabaseExpression
}

interface DatabaseSource {
    fun <T : Any> getCollection(name: String, clazz: KClass<T>): DatabaseCollection<T>
    fun <T : Any> getCollectionByPrefix(prefix: String, clazz: KClass<T>): List<DatabaseCollection<T>>
    suspend fun clearCollection(collectionName: String)

    companion object {
        val EMPTY = object : DatabaseSource {
            override fun <T : Any> getCollection(name: String, clazz: KClass<T>): DatabaseCollection<T> {
                TODO("Not yet implemented")
            }

            override fun <T : Any> getCollectionByPrefix(
                prefix: String,
                clazz: KClass<T>
            ): List<DatabaseCollection<T>> {
                TODO("Not yet implemented")
            }

            override suspend fun clearCollection(collectionName: String) {
                TODO("Not yet implemented")
            }
        }
    }
}

inline fun <reified T : Any> DatabaseCollection<T>.save(key: PrimaryKey, data: T) {
    save(key.toString(), data)
}

inline fun <reified T : Any> DatabaseCollection<T>.save(key: String, data: T) {
    saveDocument(key, data)
}

fun <T> DatabaseCollection<T>.getDocument(id: PrimaryKey): T? {
    return getDocument(id.toString())
}

fun <T> DatabaseCollection<T>.deleteDocument(id: PrimaryKey) {
    deleteDocument(id.toString())
}

fun <T> DatabaseCollection<T>.update(id: PrimaryKey, block: (T) -> T) {
    val document = getDocument(id) ?: return
    val value = block(document)
    saveDocument(id.toString(), value)
}

fun <T : Any> DatabaseSource.getKeyCollection(collectionName: String, keyClazz: KClass<T>): DatabaseCollection<T> {
    return getCollection("${collectionName}_key", keyClazz)
}
