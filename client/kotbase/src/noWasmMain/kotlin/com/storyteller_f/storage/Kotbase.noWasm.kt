package com.storyteller_f.storage

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
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotbase.Collection as KotbaseCollection

actual fun createKotbaseStorageSource(scope: String?): DocumentSource {
    return KotbaseDocumentSource(createKotbase(), scope)
}

class KotbaseObservable<T>(
    private val listenerToken: ListenerToken,
    override val deferred: CompletableDeferred<List<T>>,
) :
    DocumentObservable<T> {
    override fun remove() {
        listenerToken.remove()
    }
}


class KotbaseDocumentCollection<T>(
    val collection: KotbaseCollection,
    val source: KotbaseDocumentSource,
    val serializer: KSerializer<T>
) :
    DocumentCollection<T> {

    override fun saveDocument(id: String, t: T) {
        collection.save(MutableDocument(id, Json.encodeToString(serializer, t)))
    }

    override fun observeDatum(expression: DocumentExpression): Flow<T?> {
        return select(all()).from(collection).where {
            buildExpression(expression)
        }.limit(1).queryChangeFlow().map {
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

    override fun getDocument(expression: DocumentExpression): T? {
        return kotbase.ktx.select(all()).from(collection)
            .where {
                buildExpression(expression)
            }.execute().toObjects { jsonStr: String ->
                Json.decodeFromString(serializer, jsonStr)
            }.firstOrNull()
    }

    override fun getDocument(id: String): T? {
        return collection.getDocument(id)?.toJSON()?.let {
            Json.decodeFromString(serializer, it)
        }
    }

    override fun exists(expression: DocumentExpression): Boolean {
        return kotbase.ktx.select(all()).from(collection)
            .where {
                buildExpression(expression)
            }
            .execute().next() != null
    }

    private fun WhereBuilder.buildExpression(expression: DocumentExpression): Expression = when (expression) {
        is DocumentExpression.IdEq -> expression.field equalTo expression.value
        is DocumentExpression.StrEq -> expression.field equalTo expression.value
        is DocumentExpression.Less -> expression.field lessThan expression.value
        is DocumentExpression.StrLess -> expression.field lessThan expression.value
        is DocumentExpression.IntLess -> expression.field lessThan expression.value
    }

    override fun deleteDocument(id: String) {
        collection.getDocument(id)?.let {
            collection.delete(it)
        }
    }

    override fun observeData(
        orders: List<DocumentSourceOrder>,
        size: Int,
        vararg expressions: DocumentExpression,
        invalidate: () -> Unit,
    ): DocumentObservable<T> {
        val task = CompletableDeferred<List<T>>()
        val selectQuery = select(all()).from(collection)
        val listenerToken = if (expressions.isNotEmpty()) {
            selectQuery.where {
                expressions.toList().subList(1, expressions.size)
                    .fold(buildExpression(expressions.first())) { acc, expression ->
                        acc and buildExpression(expression)
                    }
            }
        } else {
            selectQuery
        }.orderBy {
            orders.forEach {
                when (it) {
                    is DocumentSourceOrder.Asc -> it.field.ascending()
                    is DocumentSourceOrder.Desc -> it.field.descending()
                }
            }
        }.limit(size)
            .addChangeListener { queryChange ->
                val results = queryChange.results
                when {
                    results == null -> task.completeExceptionally(queryChange.error ?: Exception("it.error is null"))
                    task.isCompleted -> {
                        if (!source.clearing.contains(collection.name))
                            invalidate()
                    }

                    else -> {
                        runCatching {
                            results.use {
                                it.toObjects { jsonData: String ->
                                    Json.decodeFromString(serializer, jsonData)
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

        return KotbaseObservable(listenerToken, task)
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

class KotbaseDocumentSource(private val database: Database, val scope: String?) : DocumentSource {
    val clearing = mutableSetOf<String>()
    val mutex = Mutex()

    @OptIn(InternalSerializationApi::class)
    override fun <T : Any> getCollection(name: String, clazz: KClass<T>): DocumentCollection<T> {
        val kotbaseScope = getKotbaseScope()
        val collection = kotbaseScope.getCollection(name) ?: database.createCollection(
            name, scope
        )
        if (name.startsWith("communities_") && !name.endsWith("keys")) {
            if (!collection.indexes.contains("poster_index"))
                collection.createIndex(
                    "poster_index",
                    ValueIndexConfiguration("hasPoster", "id")
                )
        } else if (name.startsWith("topics_") && !name.endsWith("keys")) {
            if (!collection.indexes.contains("pinned_index"))
                collection.createIndex(
                    "pinned_index",
                    ValueIndexConfiguration("pinned", "id")
                )
        }
        return KotbaseDocumentCollection(collection, this, clazz.serializer())
    }

    override fun <T : Any> getCollectionByPrefix(prefix: String, clazz: KClass<T>): List<DocumentCollection<T>> {
        return getKotbaseScope().collections.filter {
            it.name.startsWith(prefix) && it.scope.name == scope
        }.map {
            getCollection(it.name, clazz)
        }
    }

    override suspend fun clearCollection(collectionName: String) {
        val collection = getKotbaseScope().getCollection(collectionName) ?: return
        mutex.withLock {
            clearing.add(collectionName)
            try {
                select(Meta.id).from(collection).execute().use {
                    it.toObjects { map: Map<String, Any?> ->
                        collection.getDocument(map["id"].toString())?.let { document ->
                            collection.delete(document)
                        }
                    }
                }
            } finally {
                clearing.remove(collectionName)
            }
        }
    }

    private fun getKotbaseScope(): Scope {
        return scope?.let {
            database.getScope(it)
        } ?: database.defaultScope
    }
}