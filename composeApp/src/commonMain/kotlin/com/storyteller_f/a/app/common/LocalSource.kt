package com.storyteller_f.a.app.common

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import app.cash.paging.*
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.model.Identifiable
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier
import io.ktor.client.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

class CustomQueryPagingSource<Key : Any, RowType : Any>(
    collectionName: String,
    databaseSource: DatabaseSource,
    private val serializer: KSerializer<RowType>,
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
            val observerToken = collection.observeList(queryProvider(key), params.loadSize, order, serializer) {
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

@OptIn(ExperimentalPagingApi::class)
class CustomRemoteMediator<Key : Any, Datum : Any>(
    private val collectionName: String,
    private val databaseSource: DatabaseSource,
    private val key: com.storyteller_f.a.client_lib.Collection.(Datum?) -> Key?,
    private val update:
    (Datum, Key?, com.storyteller_f.a.client_lib.Collection, com.storyteller_f.a.client_lib.Collection) -> Unit,
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
                databaseSource.getCollection("${collectionName}_key").key(lastItem)
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
            mediatorResult(params, loadType, loadKey)
        } catch (e: Exception) {
            Napier.e(e, tag = "pagination") {
                "mediator load error"
            }
            MediatorResult.Error(e)
        }
    }

    private suspend fun mediatorResult(
        params: androidx.paging.PagingSource.LoadParams<Key>,
        loadType: LoadType,
        loadKey: Key?
    ) = when (val loadResult = networkService.load(params)) {
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
                update(
                    it,
                    nextKey,
                    databaseSource.getCollection(collectionName),
                    databaseSource.getCollection("${collectionName}_key")
                )
            }
            Napier.v(tag = "pagination") {
                "mediator success $loadKey"
            }
            MediatorResult.Success(
                endOfPaginationReached = nextKey == null
            )
        }
    }
}

inline fun <reified T : Identifiable> singleSourceMediator(
    databaseSource: DatabaseSource,
    collectionName: String,
    pagingSource: RegularPagingSource<T>,
) = CustomRemoteMediator(
    collectionName,
    databaseSource,
    {
        it?.id
    },
    { info, _, c, _ ->
        c.save(info.id, info)
    },
    pagingSource
)

inline fun <reified T : Identifiable> sectionMediator(
    client: HttpClient,
    collectionName: String,
    databaseSource: DatabaseSource,
    crossinline extraUpdate: (T) -> Unit = {},
    regularPagingSources: (HttpClient) -> List<RegularPagingSource<T>>
) = CustomRemoteMediator(
    collectionName,
    databaseSource,
    {
        if (it != null) {
            getDocument(it.id.toString(), serializer<SectionLoadParams<PrimaryKey>>())
        } else {
            SectionLoadParams(1, null)
        }
    },
    { info, key, c, k ->
        if (key == null) {
            k.deleteDocument(info.id)
        } else {
            k.save(info.id, key)
        }
        c.save(info.id, info)
        extraUpdate(info)
    },
    SectionPagingSource(regularPagingSources(client))
)

inline fun <reified T : Identifiable> sectionPagingSource(
    databaseSource: DatabaseSource,
    collectionName: String,
    orders: List<Order>,
    noinline extra: suspend List<T>.() -> List<T> = { this }
) = CustomQueryPagingSource(
    collectionName,
    databaseSource,
    serializer<T>(),
    { info ->
        info?.id?.let {
            SectionLoadParams(0, it)
        }
    },
    {
        val param = it?.param
        if (param != null) {
            Expression.Less("id", param)
        } else {
            null
        }
    },
    orders,
    extra
)

inline fun <reified T : Identifiable> singleSourceDatabaseSource(
    collectionName: String,
    databaseSource: DatabaseSource
) =
    CustomQueryPagingSource(
        collectionName = collectionName,
        databaseSource,
        serializer<T>(),
        key = { info ->
            info?.id
        },
        queryProvider = {
            val param = it
            if (param != null) {
                Expression.Less("id", param)
            } else {
                null
            }
        },
        order = listOf(Order.Desc("id")),
    )
