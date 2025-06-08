package com.storyteller_f.a.app.common

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import app.cash.paging.*
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.model.Identifiable
import com.storyteller_f.shared.type.toPrimaryKey
import com.storyteller_f.storage.*
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CachedLoadingHandler<T : Any>(
    private val collection: DatabaseCollection<T>,
    private val scope: CoroutineScope,
    expression: DatabaseExpression,
    private val loader: suspend () -> Result<T>,
    private val onSaveDocument: DatabaseCollection<T>.(T) -> Unit,
) : LoadingHandler<T> {
    override val state: MutableStateFlow<LoadingState?> = MutableStateFlow(null)

    @OptIn(FlowPreview::class)
    override val data = collection.observeDatum(
        expression
    ).debounce(500).stateIn(scope, SharingStarted.Lazily, null)

    init {
        refresh()
    }

    override fun done(t: T) {
        try {
            collection.onSaveDocument(t)
            state.markDown()
        } catch (e: Exception) {
            error(e)
        }
    }

    override fun error(error: Throwable) {
        state.markError(error)
    }

    override fun update(t: T) {
        done(t)
    }

    override fun refresh() {
        scope.launch {
            request {
                loader()
            }
        }
    }
}

class CustomQueryPagingSource<Key : Any, RowType : Any>(
    private val collection: DatabaseCollection<RowType>,
    private val orders: List<DatabaseOrder>,
    private val buildExpression: (Key?) -> DatabaseExpression?,
    private val extraProcessor: suspend List<RowType>.() -> List<RowType> = { this },
    private val buildNextKey: (RowType?) -> Key?
) : PagingSource<Key, RowType>() {

    init {
        registerInvalidatedCallback {
            removeAllToken()
        }
    }

    override fun getRefreshKey(state: PagingState<Key, RowType>): Key? {
        return null
    }

    private val registeredToken = mutableMapOf<Key?, DatabaseObservable<RowType>>()

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
            val observerToken = collection.observeData(orders, params.loadSize, buildExpression(key)) {
                invalidate()
            }
            registeredToken[key] = observerToken
            val data = observerToken.task.await().extraProcessor()
            PagingSourceLoadResultPage(
                data = data,
                prevKey = null,
                nextKey = buildNextKey(data.lastOrNull()),
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
    private val datumCollection: DatabaseCollection<Datum>,
    private val keyCollection: DatabaseCollection<Key>,
    private val databaseSource: DatabaseSource,
    private val collectionName: String,
    private val networkService: PagingSource<Key, Datum>,
    private val buildPreKey: DatabaseCollection<Key>.(Datum?) -> Key?,
    private val buildNextKey: DatabaseCollection<Key>.(Datum?) -> Key?,
    private val update: (Datum, Key?) -> Unit
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
                val loadKey =
                    keyCollection.buildNextKey(lastItem)
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

inline fun <reified T : Identifiable> primaryKeyRemoteMediator(
    databaseSource: DatabaseSource,
    collectionName: String,
    pagingSource: RegularPagingSource<T>,
): CustomRemoteMediator<String, T> {
    val datumCollection = databaseSource.getCollection(collectionName, T::class)
    val keyCollection = databaseSource.getKeyCollection(collectionName, String::class)
    return CustomRemoteMediator(
        datumCollection,
        keyCollection,
        databaseSource,
        collectionName,
        pagingSource,
        {
            it?.id?.toString()
        },
        {
            it?.id?.toString()
        }
    ) { info, c ->
        datumCollection.save(info.id, info)
    }
}

inline fun <reified T : Identifiable> primaryKeyPagingSource(
    collectionName: String,
    databaseSource: DatabaseSource
) =
    CustomQueryPagingSource(
        databaseSource.getCollection(collectionName, T::class),
        listOf(DatabaseOrder.Desc("id")),
        {
            val param = it
            if (param != null) {
                DatabaseExpression.Less("id", param.toPrimaryKey())
            } else {
                null
            }
        }
    ) { info ->
        info?.id?.toString()
    }

inline fun <reified T : Identifiable> sectionRemoteMediator(
    sessionManager: SessionManager,
    collectionName: String,
    databaseSource: DatabaseSource,
    crossinline extraUpdate: (T) -> Unit = {},
    regularPagingSources: (SessionManager) -> List<RegularPagingSource<T>>
): CustomRemoteMediator<SectionLoadParams, T> {
    val datumCollection = databaseSource.getCollection(collectionName, T::class)
    val keyCollection = databaseSource.getKeyCollection(collectionName, SectionLoadParams::class)
    return CustomRemoteMediator(
        datumCollection,
        keyCollection,
        databaseSource,
        collectionName,
        SectionPagingSource(regularPagingSources(sessionManager)),
        {
            if (it != null) {
                getDocument(it.id.toString())
            } else {
                SectionLoadParams(1, null)
            }
        },
        {
            if (it != null) {
                getDocument(it.id.toString())
            } else {
                SectionLoadParams(1, null)
            }
        }
    ) { info, key ->
        if (key == null) {
            keyCollection.deleteDocument(info.id)
        } else {
            keyCollection.save(info.id, key)
        }
        datumCollection.save(info.id, info)
        extraUpdate(info)
    }
}

inline fun <reified T : Identifiable> sectionPagingSource(
    databaseSource: DatabaseSource,
    collectionName: String,
    orders: List<DatabaseOrder>,
    noinline extraProcessor: suspend List<T>.() -> List<T> = { this }
) = CustomQueryPagingSource(
    databaseSource.getCollection(collectionName, T::class),
    orders,
    {
        val param = it?.param
        if (param != null) {
            DatabaseExpression.Less("id", param.toPrimaryKey())
        } else {
            null
        }
    },
    extraProcessor
) { info ->
    info?.id?.let {
        SectionLoadParams(0, it.toString())
    }
}
