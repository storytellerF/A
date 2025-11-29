package com.storyteller_f.storage

import app.cash.paging.PagingSource
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier
import kotbase.*
import kotbase.QueryBuilder.select
import kotbase.ktx.*
import kotbase.ktx.from
import kotbase.paging.QueryPagingSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotbase.Collection as KotbaseCollection

inline fun <reified T : Any> KotbaseDocumentSource.getCollection(name: String): CustomKotbaseCollection<T> {
    return getCollection(name, T::class)
}

suspend inline fun <reified T : Any> CustomKotbaseCollection<T>.save(key: PrimaryKey, data: T) {
    saveDocument(key.toString(), data)
}

suspend inline fun <reified T : Any> CustomKotbaseCollection<T>.save(key: String?, data: T) {
    key ?: return
    if (key.isEmpty()) return
    saveDocument(key, data)
}

suspend fun <T : Any> CustomKotbaseCollection<T>.getDocument(id: PrimaryKey): T? {
    return getDocument(id.toString())
}

suspend fun <T : Any> CustomKotbaseCollection<T>.deleteDocument(id: PrimaryKey) {
    deleteDocument(id.toString())
}

suspend fun <T : Any> CustomKotbaseCollection<T>.update(id: PrimaryKey, block: (T) -> T) {
    val document = getDocument(id) ?: return
    val value = block(document)
    saveDocument(id.toString(), value)
}

fun <T : Any> CustomKotbaseCollection<T>.observeDatum(id: PrimaryKey): Flow<T?> {
    return observeDatum {
        "id" equalTo id
    }
}

class CustomKotbaseCollection<T : Any>(
    val collection: KotbaseCollection,
    val serializer: KSerializer<T>
) {
    suspend fun saveDocument(id: String, t: T) {
        withContext(Dispatchers.IO) {
            collection.save(MutableDocument(id, commonJson.encodeToString(serializer, t)))
        }
    }

    fun observeDatum(function: WhereBuilder.() -> Expression): Flow<T?> {
        return select(all()).from(collection).where(function).limit(1).queryChangeFlow(Dispatchers.IO).map {
            if (it.error != null) {
                Napier.e(throwable = it.error) {
                    "get data failed from ${collection.name}"
                }
            }

            it.results?.toObjects { jsonStr: String ->
                commonJson.decodeFromString(serializer, jsonStr)
            }?.firstOrNull()
        }
    }

    suspend fun getDocument(function: WhereBuilder.() -> Expression): T? {
        return withContext(Dispatchers.IO) {
            select(all()).from(collection)
                .where(function).execute().toObjects { jsonStr: String ->
                    commonJson.decodeFromString(serializer, jsonStr)
                }.firstOrNull()
        }
    }

    suspend fun getDocument(id: String): T? {
        return withContext(Dispatchers.IO) {
            collection.getDocument(id)?.toJSON()?.let {
                commonJson.decodeFromString(serializer, it)
            }
        }
    }

    suspend fun exists(function: WhereBuilder.() -> Expression): Boolean {
        return withContext(Dispatchers.IO) {
            select(all()).from(collection)
                .where(function)
                .execute().next() != null
        }
    }

    suspend fun deleteDocument(id: String) {
        withContext(Dispatchers.IO) {
            collection.getDocument(id)?.let {
                collection.delete(it)
            }
        }
    }

    fun getSource(queryProvider: From.() -> LimitRouter): PagingSource<Int, T> {
        val mapper = { json: String ->
            commonJson.decodeFromString(serializer, json)
        }
        return QueryPagingSource(Dispatchers.IO, select(all()), collection, mapper, queryProvider)
    }
}

class KotbaseDocumentSource(val database: Database, val scope: String?) {

    @OptIn(InternalSerializationApi::class)
    fun <T : Any> getCollection(name: String, clazz: KClass<T>): CustomKotbaseCollection<T> {
        val kotbaseScope = getKotbaseScope(scope, database)
        val collection = kotbaseScope.getCollection(name) ?: database.createCollection(
            name, scope
        )
        if (name.startsWith("communities_") && !name.endsWith("keys")) {
            if (!collection.indexes.contains("poster_index")) {
                collection.createIndex("poster_index", ValueIndexConfiguration("hasPoster", "id"))
            }
        } else if (name.startsWith("topics_") && !name.endsWith("keys")) {
            if (!collection.indexes.contains("pinned_index")) {
                collection.createIndex("pinned_index", ValueIndexConfiguration("pinned", "id"))
            }
        }
        return CustomKotbaseCollection(collection, clazz.serializer())
    }

    private fun getKotbaseScope(scope: String?, database: Database): Scope {
        return scope?.let {
            database.getScope(it)
        } ?: database.defaultScope
    }
}

private fun createKotbase(): Database {
    val path = Path(SystemTemporaryDirectory, "a-client")
    if (!SystemFileSystem.exists(path)) {
        SystemFileSystem.createDirectories(path)
    }
    val p = SystemFileSystem.resolve(path)
    return Database(
        "a-db",
        DatabaseConfigurationFactory.newConfig(p.toString())
    ).apply {
        Database.log.console.domains = LogDomain.ALL_DOMAINS
        Database.log.console.level = LogLevel.WARNING
    }
}

fun createKotbaseSource(scope: String?): KotbaseDocumentSource {
    return KotbaseDocumentSource(createKotbase(), scope)
}
