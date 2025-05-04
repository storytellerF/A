package com.storyteller_f.a.client_lib

import io.github.aakira.napier.Napier
import kotbase.*
import kotbase.QueryBuilder.select
import kotbase.ValueIndexConfiguration
import kotbase.ktx.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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


class KotbaseDatabaseCollection(private val collection: kotbase.Collection) : DatabaseCollection {
    override fun saveDocument(id: String, string: String) {
        collection.save(MutableDocument(id, string))
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
        order: List<Order>,
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
            order.forEach {
                when (it) {
                    is Order.Asc -> {
                        it.field.ascending()
                    }

                    is Order.Desc -> {
                        it.field.descending()
                    }

                    is Order.NotNull -> {
                        Ordering.expression(kotbase.Expression.property(it.field).isNotValued())
                    }
                }
            }
        }
            .limit(size)
            .addChangeListener { queryChange ->
                val results = queryChange.results
                when {
                    task.isCompleted -> invalidate()
                    results == null -> task.completeExceptionally(queryChange.error ?: Exception("it.error is null"))
                    else -> {
                        runCatching {
                            results.toObjects { jsonData: String ->
                                json.decodeFromString(serializer, jsonData)
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
        Database.log.console.level = LogLevel.WARNING
    }
}

class KotbaseDatabaseSource(private val database: Database) : DatabaseSource {

    override fun getCollection(name: String): DatabaseCollection {
        val collection = database.defaultScope.getCollection(name) ?: database.createCollection(
            name
        )
        if (name.startsWith("communities_")) {
            collection.createIndex(
                "poster_index",
                ValueIndexConfiguration("poster")
            )
        } else if (name.startsWith("topics_") && name != "topics_keys") {
            collection.createIndex(
                "pinned_index",
                ValueIndexConfiguration("pinned")
            )
        }
        return KotbaseDatabaseCollection(collection)
    }

    override fun getCollectionByPrefix(prefix: String): List<DatabaseCollection> {
        return database.defaultScope.collections.filter {
            it.name.startsWith(prefix)
        }.map {
            KotbaseDatabaseCollection(it)
        }
    }

    override fun deleteCollection(collectionName: String) {
        database.deleteCollection(name = collectionName)
    }
}