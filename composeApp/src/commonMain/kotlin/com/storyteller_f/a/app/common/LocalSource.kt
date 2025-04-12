package com.storyteller_f.a.app.common

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import app.cash.paging.*
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.a.client_lib.Expression
import com.storyteller_f.shared.model.Identifiable
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier
import kotbase.*
import kotbase.Collection
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

class CustomQueryPagingSource<Key : Any, RowType : Any>(
    collectionName: String,
    val databaseSource: DatabaseSource,
    val serializer: KSerializer<RowType>,
    private val key: (RowType?) -> Key?,
    private val queryProvider: (Key?) -> Expression?,
    private val order: List<Order>,
    private val extraProcessor: suspend List<RowType>.() -> List<RowType> = { this }
) : PagingSource<Key, RowType>() {

    private val collection = databaseSource.getCollection(collectionName)

    init {
        registerInvalidatedCallback {
            removeAllToken()
        }
    }

    override fun getRefreshKey(state: PagingState<Key, RowType>): Key? {
        return null
    }

    val map = mutableMapOf<Key?, ObserverToken<RowType>>()

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
            val observerToken = collection.observe(queryProvider(key), params.loadSize, order, serializer) {
                invalidate()
            }
            val task = observerToken.task
            map[key] = observerToken
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
    id: PrimaryKey,
    databaseSource: DatabaseSource
) {
    val collection = databaseSource.getCollection("${collectionName}_key")
    if (key == null) {
        collection.deleteDocument(id.toString())
    } else {
        val json = Json.encodeToString(key)
        collection.saveDocument(id.toString(), json)
    }
}

fun getSectionLoadParams(
    collectionName: String,
    id: PrimaryKey,
    databaseSource: DatabaseSource,
) = databaseSource.getCollection("${collectionName}_key")
    .getDocument(id.toString(), serializer<SectionLoadParams<PrimaryKey>>())

@OptIn(ExperimentalPagingApi::class)
class CustomRemoteMediator<Key : Any, Datum : Any>(
    private val collectionName: String,
    private val databaseSource: DatabaseSource,
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
                        databaseSource.deleteCollection(collectionName)
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
    pagingSource: RegularPagingSource<T>,
    databaseSource: DatabaseSource,
) = CustomRemoteMediator(
    collectionName,
    databaseSource,
    {
        it?.id
    },
    { info, _ ->
        databaseSource.getCollection(collectionName).save(
            info.id.toString(),
            Json.encodeToString(info)
        )
    },
    pagingSource
)
