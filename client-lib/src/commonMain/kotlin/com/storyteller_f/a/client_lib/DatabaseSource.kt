package com.storyteller_f.a.client_lib

import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier
import kotbase.*
import kotbase.QueryBuilder.select
import kotbase.ktx.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

interface ObserverToken<T> {
    val task: CompletableDeferred<List<T>>

    fun remove()
}

sealed interface Order {
    data class NotNull(val field: String) : Order
    data class Asc(val field: String) : Order
    data class Desc(val field: String) : Order
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

interface Collection {
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
    fun getCollection(name: String): Collection
    fun deleteCollection(collectionName: String)

    companion object {
        val EMPTY = object : DatabaseSource {
            override fun getCollection(name: String): Collection {
                TODO("Not yet implemented")
            }

            override fun deleteCollection(collectionName: String) {
                TODO("Not yet implemented")
            }
        }
    }
}

class KotbaseCollection(private val collection: kotbase.Collection) : Collection {
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
            .addChangeListener {
                val results = it.results
                when {
                    task.isCompleted -> invalidate()
                    results == null -> task.completeExceptionally(it.error ?: Exception("it.error is null"))
                    else -> {
                        task.complete(results.toObjects { json: String ->
                            Json.decodeFromString(serializer, json)
                        })
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
    private fun getOrCreateCollection(collectionName: String): kotbase.Collection {
        val collection = database.defaultScope.getCollection(collectionName) ?: database.createCollection(
            collectionName
        )
        if (collectionName.startsWith("communities_")) {
            collection.createIndex(
                "poster_index",
                ValueIndexConfiguration("poster")
            )
        } else if (collectionName.startsWith("topics_") && collectionName != "topics_keys") {
            collection.createIndex(
                "pinned_index",
                ValueIndexConfiguration("pinned")
            )
        }
        return collection
    }

    override fun getCollection(name: String): Collection {
        return KotbaseCollection(getOrCreateCollection(name))
    }

    override fun deleteCollection(collectionName: String) {
        database.deleteCollection(name = collectionName)
    }
}

fun Collection.save(key: String, data: String) {
    saveDocument(key, data)
}

fun Collection.save(key: PrimaryKey, data: String) {
    saveDocument(key.toString(), data)
}

inline fun <reified T : Any> Collection.save(key: PrimaryKey, data: T) {
    saveDocument(key.toString(), Json.encodeToString(data))
}

inline fun <reified T : Any> Collection.save(key: String, data: T) {
    saveDocument(key, Json.encodeToString(data))
}
