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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

class CustomQueryPagingSource<Key : Any, RowType : Any>(
    val collectionName: String,
    val scopeName: String?,
    val databaseSource: DatabaseSource,
    private val serializer: KSerializer<RowType>,
    private val key: (RowType?) -> Key?,
    private val queryProvider: (Key?) -> Expression?,
    private val orders: List<Order>,
    private val extraProcessor: suspend List<RowType>.() -> List<RowType> = { this }
) : PagingSource<Key, RowType>() {

    init {
        registerInvalidatedCallback {
            removeAllToken()
        }
    }

    override fun getRefreshKey(state: PagingState<Key, RowType>): Key? {
        return null
    }

    private val registeredToken = mutableMapOf<Key?, ObserverToken<RowType>>()

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
            val collection = databaseSource.getCollection(collectionName, scopeName)
            val observerToken = collection.observeList(queryProvider(key), params.loadSize, orders, serializer) {
                invalidate()
            }
            val task = observerToken.task
            registeredToken[key] = observerToken
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
        registeredToken.values.forEach {
            it.remove()
        }
        registeredToken.clear()
    }
}

@Suppress("unused")
@OptIn(ExperimentalPagingApi::class)
class CustomRemoteMediator<Key : Any, Datum : Any>(
    private val collectionName: String,
    private val scopeName: String?,
    private val databaseSource: DatabaseSource,
    private val preKey: DatabaseCollection.(Datum?) -> Key?,
    private val nextKey: DatabaseCollection.(Datum?) -> Key?,
    private val update: (Datum, Key?, DatabaseCollection, DatabaseCollection) -> Unit,
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
        val params = when (loadType) {
            LoadType.REFRESH -> PagingSourceLoadParamsRefresh<Key>(
                null,
                state.config.pageSize,
                state.config.enablePlaceholders
            )

            LoadType.PREPEND -> {
                return MediatorResult.Success(endOfPaginationReached = true)
//                val lastItem = state.firstItemOrNull()
//                val loadKey = databaseSource.getCollection("${collectionName}_pre_key").preKey(lastItem)
//                    ?: return MediatorResult.Success(endOfPaginationReached = true)
//                PagingSourceLoadParamsPrepend(loadKey, state.config.pageSize, state.config.enablePlaceholders)
            }

            LoadType.APPEND -> {
                val lastItem = state.lastItemOrNull()
                val loadKey = databaseSource.getCollection("${collectionName}_next_key", scopeName).nextKey(lastItem)
                    ?: return MediatorResult.Success(endOfPaginationReached = true)
                PagingSourceLoadParamsAppend(
                    loadKey,
                    state.config.pageSize,
                    state.config.enablePlaceholders
                )
            }
        }
        return try {
            mediatorResult(params, loadType)
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
                databaseSource.clearCollection(collectionName)
            }
            data.forEach {
                update(
                    it,
                    nextKey,
                    databaseSource.getCollection(collectionName, scopeName),
                    databaseSource.getCollection("${collectionName}_key", scopeName)
                )
            }
            Napier.v(tag = "pagination") {
                "mediator success $loadType $params"
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
    scopeName: String?,
    pagingSource: RegularPagingSource<T>,
) = CustomRemoteMediator(
    collectionName,
    scopeName,
    databaseSource,
    {
        it?.id
    },
    {
        it?.id
    },
    { info, _, c, _ ->
        c.save(info.id, info)
    },
    pagingSource
)

inline fun <reified T : Identifiable> sectionMediator(
    sessionManager: SessionManager,
    collectionName: String,
    scopeName: String?,
    databaseSource: DatabaseSource,
    crossinline extraUpdate: (T) -> Unit = {},
    regularPagingSources: (SessionManager) -> List<RegularPagingSource<T>>
) = CustomRemoteMediator(
    collectionName,
    scopeName,
    databaseSource,
    {
        if (it != null) {
            getDocument(it.id.toString(), serializer<SectionLoadParams<PrimaryKey>>())
        } else {
            SectionLoadParams(1, null)
        }
    },
    {
        if (it != null) {
            getDocument(it.id.toString(), serializer<SectionLoadParams<PrimaryKey>>())
        } else {
            SectionLoadParams(1, null)
        }
    },
    { info, key, mainCollection, keyCollection ->
        if (key == null) {
            keyCollection.deleteDocument(info.id)
        } else {
            keyCollection.save(info.id, key)
        }
        mainCollection.save(info.id, info)
        extraUpdate(info)
    },
    SectionPagingSource(regularPagingSources(sessionManager))
)

inline fun <reified T : Identifiable> sectionPagingSource(
    databaseSource: DatabaseSource,
    collectionName: String,
    scopeName: String?,
    orders: List<Order>,
    noinline extra: suspend List<T>.() -> List<T> = { this }
) = CustomQueryPagingSource(
    collectionName,
    scopeName,
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
    scopeName: String?,
    databaseSource: DatabaseSource
) =
    CustomQueryPagingSource(
        collectionName,
        scopeName,
        databaseSource,
        serializer<T>(),
        { info ->
            info?.id
        },
        {
            val param = it
            if (param != null) {
                Expression.Less("id", param)
            } else {
                null
            }
        },
        listOf(Order.Desc("id"))
    )
