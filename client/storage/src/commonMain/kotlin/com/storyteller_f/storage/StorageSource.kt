package com.storyteller_f.storage

import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

interface StorageObservable<T> {
    val task: CompletableDeferred<List<T>>

    fun remove()
}

sealed interface StorageOrder {
    data class Asc(val field: String) : StorageOrder
    data class Desc(val field: String) : StorageOrder
}

interface StorageCollection<T> {
    fun saveDocument(id: String, t: T)
    fun getDocument(expression: StorageExpression): T?
    fun getDocument(id: String): T?
    fun exists(expression: StorageExpression): Boolean
    fun deleteDocument(id: String)
    fun observeDatum(expression: StorageExpression): Flow<T?>
    fun observeData(
        orders: List<StorageOrder>,
        size: Int,
        vararg expressions: StorageExpression,
        invalidate: () -> Unit
    ): StorageObservable<T>
}

sealed interface StorageExpression {
    data class IdEq(val field: String, val value: PrimaryKey) : StorageExpression
    data class StrEq(val field: String, val value: String) : StorageExpression
    data class Less(val field: String, val value: PrimaryKey) : StorageExpression
    data class StrLess(val field: String, val value: String) : StorageExpression
}

interface StorageSource {
    fun <T : Any> getCollection(name: String, clazz: KClass<T>): StorageCollection<T>
    fun <T : Any> getCollectionByPrefix(prefix: String, clazz: KClass<T>): List<StorageCollection<T>>
    suspend fun clearCollection(collectionName: String)

    companion object {
        val EMPTY = object : StorageSource {
            override fun <T : Any> getCollection(name: String, clazz: KClass<T>): StorageCollection<T> {
                TODO("Not yet implemented")
            }

            override fun <T : Any> getCollectionByPrefix(
                prefix: String,
                clazz: KClass<T>
            ): List<StorageCollection<T>> {
                TODO("Not yet implemented")
            }

            override suspend fun clearCollection(collectionName: String) {
                TODO("Not yet implemented")
            }
        }
    }
}

inline fun <reified T : Any> StorageCollection<T>.save(key: PrimaryKey, data: T) {
    saveDocument(key.toString(), data)
}

fun <T> StorageCollection<T>.getDocument(id: PrimaryKey): T? {
    return getDocument(id.toString())
}

fun <T> StorageCollection<T>.deleteDocument(id: PrimaryKey) {
    deleteDocument(id.toString())
}

fun <T> StorageCollection<T>.update(id: PrimaryKey, block: (T) -> T) {
    val document = getDocument(id) ?: return
    val value = block(document)
    saveDocument(id.toString(), value)
}

fun <T : Any> StorageSource.getKeyCollection(collectionName: String, keyClazz: KClass<T>): StorageCollection<T> {
    return getCollection("${collectionName}_key", keyClazz)
}
