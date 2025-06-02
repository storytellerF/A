package com.storyteller_f.a.client_lib

import io.github.aakira.napier.Napier
import kotbase.*
import kotbase.QueryBuilder.select
import kotbase.ktx.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

actual fun createPlatformDatabaseSource(): DatabaseSource {
    return KotbaseDatabaseSource(createKotbase())
}

class KotbaseObserverToken<T>(
    private val listenerToken: ListenerToken,
    override val task: CompletableDeferred<List<T>>
) :
    ObserverToken<T> {
    override fun remove() {
        listenerToken.remove()
    }
}


class KotbaseDatabaseCollection(val collection: kotbase.Collection, val source: KotbaseDatabaseSource) :
    DatabaseCollection {
    override fun saveDocument(id: String, string: String) {
        try {
            collection.save(MutableDocument(id, string))
        } catch (e: Exception) {
            Napier.e(throwable = e, tag = "DatabaseSource") {
                "save data failed to ${collection.name}"
            }
        }
    }

    override fun <T : Any> observe(serializer: KSerializer<T>, expression: Expression): Flow<T?> {
        return select(all()).from(collection).where(getExpressionBuilder(expression)).limit(1).queryChangeFlow().map {
            if (it.error != null) {
                Napier.e(throwable = it.error) {
                    "get data failed from ${collection.name}"
                }
            }

            it.results?.toObjects { jsonStr: String ->
                Json.decodeFromString(serializer, jsonStr)
            }?.firstOrNull()
        }
    }

    override fun <T> getDocument(expression: Expression, serializer: KSerializer<T>): T? {
        return kotbase.ktx.select(all()).from(collection)
            .where(getExpressionBuilder(expression)).execute().toObjects { jsonStr: String ->
                Json.decodeFromString(serializer, jsonStr)
            }.firstOrNull()
    }

    override fun <T> getDocument(key: String, serializer: KSerializer<T>): T? {
        return collection.getDocument(key)?.toJSON()?.let {
            Json.decodeFromString(serializer, it)
        }
    }

    override fun exists(expression: Expression): Boolean {
        return kotbase.ktx.select(all()).from(collection)
            .where(getExpressionBuilder(expression)).execute().next() != null
    }

    private fun getExpressionBuilder(expression: Expression): WhereBuilder.() -> kotbase.Expression =
        {
            when (expression) {
                is Expression.IdEq -> {
                    (expression.field) equalTo (expression.value)
                }

                is Expression.StrEq -> {
                    (expression.field) equalTo (expression.value)
                }

                is Expression.Less -> expression.field lessThan expression.value
            }
        }

    override fun deleteDocument(key: String) {
        collection.getDocument(key)?.let {
            collection.delete(it)
        }
    }

    override fun <T> observeList(
        expression: Expression?,
        size: Int,
        orders: List<Order>,
        serializer: KSerializer<T>,
        invalidate: () -> Unit
    ): ObserverToken<T> {
        val task = CompletableDeferred<List<T>>()
        val selectQuery = select(all()).from(collection)
        val listenerToken = if (expression != null) {
            selectQuery.where(getExpressionBuilder(expression))
        } else {
            selectQuery
        }.orderBy {
            orders.forEach {
                when (it) {
                    is Order.Asc -> it.field.ascending()
                    is Order.Desc -> it.field.descending()
                }
            }
        }
            .limit(size)
            .addChangeListener { queryChange ->
                val results = queryChange.results
                when {
                    results == null -> task.completeExceptionally(queryChange.error ?: Exception("it.error is null"))
                    task.isCompleted -> {
                        if (!(source.clearing.contains(collection.name)))
                            invalidate()
                    }

                    else -> {
                        runCatching {
                            results.use {
                                it.toObjects { jsonData: String ->
                                    json.decodeFromString(serializer, jsonData)
                                }
                            }
                        }.onSuccess {
                            task.complete(it)
                        }.onFailure {
                            task.complete(emptyList())
                        }
                    }
                }
            }

        return KotbaseObserverToken(listenerToken, task)
    }
}

fun createKotbase(): Database {
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
        Database.log.console.level = LogLevel.VERBOSE
    }
}

class KotbaseDatabaseSource(private val database: Database) : DatabaseSource {
    val clearing = mutableSetOf<String>()
    val mutex = Mutex()
    override fun getCollection(name: String, scope: String?): DatabaseCollection {
        val collection = database.defaultScope.getCollection(name) ?: database.createCollection(
            name, scope
        )
        if (name.startsWith("communities_")) {
            if (!collection.indexes.contains("poster_index"))
                collection.createIndex(
                    "poster_index",
                    ValueIndexConfiguration("hasPoster", "id")
                )
        } else if (name.startsWith("topics_") && name != "topics_keys") {
            if (!collection.indexes.contains("pinned_index"))
                collection.createIndex(
                    "pinned_index",
                    ValueIndexConfiguration("pinned", "id")
                )
        }
        return KotbaseDatabaseCollection(collection, this)
    }

    override fun getCollectionByPrefix(prefix: String, scope: String?): List<DatabaseCollection> {
        return database.defaultScope.collections.filter {
            it.name.startsWith(prefix)
        }.map {
            getCollection(it.name, scope)
        }
    }

    override suspend fun clearCollection(collectionName: String) {
        val collection = database.defaultScope.getCollection(collectionName) ?: return
        mutex.withLock {
            clearing.add(collectionName)
            select(Meta.id).from(collection).execute().use {
                it.toObjects { map: Map<String, Any?> ->
                    collection.getDocument(map["id"].toString())?.let {
                        collection.delete(it)
                    }
                }
            }
            clearing.remove(collectionName)
        }
    }
}