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
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

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

class CustomDatabasePagingSource<RowType : Any>(
    private val collection: DatabaseCollection<RowType>,
    private val orders: List<DatabaseOrder>,
    private val buildExpressions: (String?) -> Array<DatabaseExpression>,
    private val buildKey: (RowType?) -> String?
) : PagingSource<String, RowType>() {

    init {
        registerInvalidatedCallback {
            removeAllToken()
        }
    }

    override fun getRefreshKey(state: PagingState<String, RowType>): String? {
        return null
    }

    private val registeredToken = mutableMapOf<String?, DatabaseObservable<RowType>>()

    override suspend fun load(
        params: PagingSourceLoadParams<String>
    ): PagingSourceLoadResult<String, RowType> {
        val key = when (params) {
            is LoadParams.Append<*> -> {
                params.key
            }

            is LoadParams.Prepend<*> -> return PagingSourceLoadResultPage(emptyList(), null, null)
            is LoadParams.Refresh<*> -> {
                removeAllToken()
                null
            }
        }
        Napier.v(tag = "pagination") {
            "source load ${params.key} $key"
        }
        return try {
            val expressions = buildExpressions(key)
            val observable = collection.observeData(orders, params.loadSize, *expressions) {
                invalidate()
            }
            registeredToken[params.key] = observable
            val data = observable.task.await()
            PagingSourceLoadResultPage(
                data = data,
                prevKey = buildKey(data.firstOrNull()),
                nextKey = buildKey(data.lastOrNull()),
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

@Serializable
data class RemoteKeys(val collectionName: String, val nextKey: String?)

@Suppress("unused")
@OptIn(ExperimentalPagingApi::class)
class CustomRemoteMediator<Datum : Any>(
    private val databaseSource: DatabaseSource,
    private val collectionName: String,
    private val networkService: PagingSource<String, Datum>,
    private val update: (Datum) -> Unit
) :
    RemoteMediator<String, Datum>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<String, Datum>
    ): MediatorResult {
        Napier.v(tag = "pagination") {
            "mediator load $loadType"
        }
        val params = when (loadType) {
            LoadType.REFRESH -> PagingSourceLoadParamsRefresh<String>(
                null,
                state.config.pageSize,
                state.config.enablePlaceholders
            )

            LoadType.PREPEND -> {
                val remoteKey =
                    databaseSource.getCollection("pre_remote_keys", RemoteKeys::class)
                        .getDocument(collectionName)?.nextKey
                PagingSourceLoadParamsAppend(
                    remoteKey
                        ?: return MediatorResult.Success(endOfPaginationReached = true),
                    state.config.pageSize,
                    state.config.enablePlaceholders
                )
            }

            LoadType.APPEND -> {
                val remoteKey =
                    databaseSource.getCollection("next_remote_keys", RemoteKeys::class)
                        .getDocument(collectionName)?.nextKey
                PagingSourceLoadParamsAppend(
                    remoteKey
                        ?: return MediatorResult.Success(endOfPaginationReached = true),
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
        params: androidx.paging.PagingSource.LoadParams<String>,
        loadType: LoadType,
    ) = when (val loadResult = networkService.load(params)) {
        is PagingSourceLoadResultError<String, Datum> -> {
            MediatorResult.Error(loadResult.throwable)
        }

        is PagingSourceLoadResultInvalid<String, Datum> -> {
            MediatorResult.Error(Exception("invalid"))
        }

        is PagingSourceLoadResultPage<String, Datum> -> {
            val data = loadResult.data
            val nextKey = loadResult.nextKey
            val preKey = loadResult.prevKey
            if (loadType == LoadType.REFRESH) {
                databaseSource.getCollection("next_remote_keys", RemoteKeys::class).deleteDocument(collectionName)
                databaseSource.getCollection("pre_remote_keys", RemoteKeys::class).deleteDocument(collectionName)
                databaseSource.clearCollection(collectionName)
            }

            databaseSource.getCollection("next_remote_keys", RemoteKeys::class)
                .saveDocument(collectionName, RemoteKeys(collectionName, nextKey))
            databaseSource.getCollection("pre_remote_keys", RemoteKeys::class)
                .saveDocument(collectionName, RemoteKeys(collectionName, preKey))
            data.forEach {
                update(it)
            }
            Napier.v(tag = "pagination") {
                "mediator success type: $loadType key: ${params.key}"
            }
            MediatorResult.Success(
                endOfPaginationReached = nextKey == null
            )
        }
    }
}

class IntermediatePagingSource<Key : Any, T : Any>(
    val sectionPagingSource: PagingSource<Key, T>,
    val clazz: KClass<Key>
) : PagingSource<String, T>() {
    val json = Json { }
    override fun getRefreshKey(state: PagingState<String, T>): String? {
        return null
    }

    @OptIn(InternalSerializationApi::class)
    override suspend fun load(params: LoadParams<String>): LoadResult<String, T> {
        val serializer = clazz.serializer()
        val loadParams: LoadParams<Key> = when (params) {
            is LoadParams.Append<*> -> {
                val key = params.key.let {
                    json.decodeFromString(serializer, it)
                }
                LoadParams.Append(key, params.loadSize, params.placeholdersEnabled)
            }

            is LoadParams.Prepend<*> -> {
                val key = params.key.let {
                    json.decodeFromString(serializer, it)
                }
                LoadParams.Prepend(key, params.loadSize, params.placeholdersEnabled)
            }

            is LoadParams.Refresh<*> -> LoadParams.Refresh(null, params.loadSize, params.placeholdersEnabled)
        }
        val load = sectionPagingSource.load(loadParams)
        return when (load) {
            is LoadResult.Error<Key, T> -> LoadResult.Error(load.throwable)
            is LoadResult.Invalid<Key, T> -> LoadResult.Invalid()
            is LoadResult.Page<Key, T> -> {
                val preKey = load.prevKey?.let { json.encodeToString(serializer, it) }
                val nextKey = load.nextKey?.let { json.encodeToString(serializer, it) }
                LoadResult.Page(load.data, preKey, nextKey)
            }
        }
    }
}

inline fun <reified T : Identifiable> primaryKeyRemoteMediator(
    databaseSource: DatabaseSource,
    collectionName: String,
    pagingSource: RegularPagingSource<T>,
): CustomRemoteMediator<T> {
    val datumCollection = databaseSource.getCollection(collectionName, T::class)
    return CustomRemoteMediator(
        databaseSource,
        collectionName,
        pagingSource,
    ) { info ->
        datumCollection.save(info.id, info)
    }
}

inline fun <reified T : Identifiable> primaryKeyPagingSource(
    collectionName: String,
    databaseSource: DatabaseSource
) =
    CustomDatabasePagingSource(
        databaseSource.getCollection(collectionName, T::class),
        listOf(DatabaseOrder.Desc("id")),
        {
            val param = it
            if (param != null) {
                arrayOf(DatabaseExpression.Less("id", param.toPrimaryKey()))
            } else {
                emptyArray()
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
): CustomRemoteMediator<T> {
    val datumCollection = databaseSource.getCollection(collectionName, T::class)
    return CustomRemoteMediator(
        databaseSource,
        collectionName,
        IntermediatePagingSource(SectionPagingSource(regularPagingSources(sessionManager)), SectionLoadParams::class),
    ) { info ->
        datumCollection.save(info.id, info)
        extraUpdate(info)
    }
}

inline fun <reified T : Any> commonRemoteMediator(
    databaseSource: DatabaseSource,
    collectionName: String,
    pagingSource: RegularPagingSource<T>,
    crossinline update: DatabaseCollection<T>.(T) -> Unit
): CustomRemoteMediator<T> {
    val datumCollection = databaseSource.getCollection(collectionName, T::class)
    return CustomRemoteMediator(
        databaseSource,
        collectionName,
        pagingSource,
    ) { info ->
        datumCollection.update(info)
    }
}

inline fun <reified T : Any> commonPagingSource(
    collectionName: String,
    databaseSource: DatabaseSource,
    noinline buildKey: (T?) -> String?
) =
    CustomDatabasePagingSource(
        databaseSource.getCollection(collectionName, T::class),
        listOf(DatabaseOrder.Desc("id")),
        {
            val param = it
            if (param != null) {
                arrayOf(DatabaseExpression.StrLess("id", param))
            } else {
                emptyArray()
            }
        },
        buildKey
    )
