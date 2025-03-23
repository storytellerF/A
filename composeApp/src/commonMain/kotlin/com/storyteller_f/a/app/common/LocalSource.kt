package com.storyteller_f.a.app.common

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import app.cash.paging.*
import com.storyteller_f.shared.model.Identifiable
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier
import kotbase.*
import kotbase.Collection
import kotbase.ktx.from
import kotbase.ktx.limit
import kotbase.ktx.toObjects
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json

val database by lazy {
    Database(
        "a-db",
        DatabaseConfigurationFactory.newConfig(
            okio.FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("a-client").toString()
        )
    ).apply {
        Database.log.console.domains = LogDomain.ALL_DOMAINS
        Database.log.console.level = LogLevel.WARNING
    }
}

fun getOrCreateCollection(collectionName: String): Collection {
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

class CustomQueryPagingSource<Key : Any, RowType : Any>(
    private val select: Select,
    collectionName: String,
    private val key: (RowType?) -> Key?,
    private val queryProvider: From.(Key?) -> LimitRouter,
    private val mapMapper: ((Map<String, Any?>) -> RowType?)? = null,
    private val jsonStringMapper: ((String) -> RowType?)? = null,
    private val extraProcessor: suspend List<RowType>.() -> List<RowType> = { this }
) : PagingSource<Key, RowType>() {

    private val collection = getOrCreateCollection(collectionName)

    init {
        require(mapMapper != null || jsonStringMapper != null) { "At least one mapper must be not null" }

        registerInvalidatedCallback {
            removeAllToken()
        }
    }

    override fun getRefreshKey(state: PagingState<Key, RowType>): Key? {
        return null
    }

    private fun ResultSet.toObjects(): List<RowType> {
        return if (mapMapper != null) {
            toObjects(mapMapper)
        } else {
            toObjects(jsonStringMapper!!)
        }
    }

    val map = mutableMapOf<Key?, ListenerToken>()

    override suspend fun load(
        params: PagingSourceLoadParams<Key>
    ): PagingSourceLoadResult<Key, RowType> {
        val key = params.key
        if (key == null) {
            removeAllToken()
        }
        Napier.v(tag = "pagination") {
            "source load $key"
        }
        return try {
            val task = CompletableDeferred<List<RowType>>()
            val listenerToken = select.from(collection)
                .queryProvider(key)
                .limit(params.loadSize)
                .addChangeListener {
                    val results = it.results
                    when {
                        task.isCompleted -> invalidate()
                        results == null -> task.completeExceptionally(it.error ?: Exception("it.error is null"))
                        else -> task.complete(results.toObjects())
                    }
                }
            map[key] = listenerToken
            val data = task.await().extraProcessor()
            PagingSourceLoadResultPage(
                data = data,
                prevKey = null,
                nextKey = key(data.lastOrNull()),
            )
        } catch (e: Exception) {
            Napier.e(e, tag = "pagination") {
                "source load error"
            }
            PagingSourceLoadResultError(e)
        }
    }

    private fun removeAllToken() {
        map.values.forEach {
            it.remove()
        }
        map.clear()
    }
}

fun saveSectionLoadParams(
    collectionName: String,
    key: SectionLoadParams<PrimaryKey>?,
    id: PrimaryKey
) {
    val collection = getOrCreateCollection("${collectionName}_key")
    if (key == null) {
        collection.getDocument(id.toString())?.let {
            collection.delete(it)
        }
    } else {
        val json = Json.encodeToString(key)
        collection.save(id, json)
    }
}

fun getSectionLoadParams(
    collectionName: String,
    id: PrimaryKey
) = getOrCreateCollection("${collectionName}_key").getDocument(id.toString())?.toJSON()?.let {
    Json.decodeFromString<SectionLoadParams<PrimaryKey>>(it)
}

@OptIn(ExperimentalPagingApi::class)
class CustomRemoteMediator<Key : Any, Datum : Any>(
    private val collectionName: String,
    private val key: (Datum?) -> Key?,
    private val update: (Datum, Key?) -> Unit,
    private val networkService: PagingSource<Key, Datum>
) :
    RemoteMediator<Key, Datum>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Key, Datum>
    ): MediatorResult {
        Napier.v(tag = "pagination") {
            "mediator load $loadType"
        }
        val loadKey = when (loadType) {
            LoadType.REFRESH -> null
            LoadType.PREPEND -> return MediatorResult.Success(
                endOfPaginationReached = true
            )

            LoadType.APPEND -> {
                val lastItem = state.lastItemOrNull()
                key(lastItem)
            }
        }
        return try {
            val params = when (loadKey) {
                null -> PagingSourceLoadParamsRefresh<Key>(
                    null,
                    state.config.pageSize,
                    state.config.enablePlaceholders
                )

                else -> PagingSourceLoadParamsAppend(
                    loadKey,
                    state.config.pageSize,
                    state.config.enablePlaceholders
                )
            }
            when (val loadResult = networkService.load(params)) {
                is PagingSourceLoadResultError<Key, Datum> -> {
                    MediatorResult.Error(loadResult.throwable)
                }

                is PagingSourceLoadResultInvalid<Key, Datum> -> {
                    MediatorResult.Error(Exception("invalid"))
                }

                is PagingSourceLoadResultPage<Key, Datum> -> {
                    val data = loadResult.data
                    val nextKey = loadResult.nextKey
                    if (loadType == LoadType.REFRESH) {
                        database.deleteCollection(collectionName)
                    }
                    data.forEach {
                        update(it, nextKey)
                    }
                    Napier.v(tag = "pagination") {
                        "mediator success $loadKey"
                    }
                    MediatorResult.Success(
                        endOfPaginationReached = nextKey == null
                    )
                }
            }
        } catch (e: Exception) {
            Napier.e(e, tag = "pagination") {
                "mediator load error"
            }
            MediatorResult.Error(e)
        }
    }
}

fun Collection.save(id: PrimaryKey, data: String) {
    save(MutableDocument(id.toString(), data))
}

fun Collection.save(id: String, data: String) {
    save(MutableDocument(id, data))
}

inline fun <reified T : Identifiable> singleSourceMediator(
    collectionName: String,
    pagingSource: RegularPagingSource<T>
) = CustomRemoteMediator(
    collectionName,
    {
        it?.id
    },
    { info, _ ->
        getOrCreateCollection(collectionName).save(
            MutableDocument(
                info.id.toString(),
                Json.encodeToString(info)
            )
        )
    },
    pagingSource
)
