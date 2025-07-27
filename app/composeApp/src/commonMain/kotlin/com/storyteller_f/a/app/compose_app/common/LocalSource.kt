package com.storyteller_f.a.app.compose_app.common

import androidx.paging.*
import com.storyteller_f.a.client.core.*
import com.storyteller_f.storage.*
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

class CachedLoadingHandler<T : Any>(
    flow: Flow<T?>,
    private val scope: CoroutineScope,
    private val onSaveDocument: (T) -> Unit,
    private val loader: suspend () -> Result<T>
) : LoadingHandler<T> {
    override val state: MutableStateFlow<LoadingState?> = MutableStateFlow(null)

    @OptIn(FlowPreview::class)
    override val data = flow.stateIn(scope, SharingStarted.Lazily, null)

    init {
        refresh()
    }

    override fun done(t: T) {
        try {
            onSaveDocument(t)
            state.markDown()
        } catch (e: Exception) {
            error(e)
        }
    }

    override fun refresh() {
        scope.launch {
            request {
                loader()
            }
        }
    }
}

class CustomStoragePagingSource<RowType : Any>(
    private val observable: (String?, Int, () -> Unit) -> DocumentObservable<RowType>,
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

    private val registeredToken = mutableMapOf<String?, DocumentObservable<RowType>>()

    override suspend fun load(
        params: LoadParams<String>,
    ): LoadResult<String, RowType> {
        val key = when (params) {
            is LoadParams.Append<*> -> {
                params.key
            }

            is LoadParams.Prepend<*> -> return LoadResult.Page(emptyList(), null, null)
            is LoadParams.Refresh<*> -> {
                removeAllToken()
                null
            }
        }
        Napier.v(tag = "pagination") {
            "source load ${params.key} $key"
        }
        return try {
            val observable = observable(key, params.loadSize) {
                invalidate()
            }
            registeredToken[params.key] = observable
            val data = observable.deferred.await()
            val prevKey = buildKey(data.firstOrNull())
            val nextKey = buildKey(data.lastOrNull())
            Napier.v(tag = "pagination") {
                "source load success key: ${params.key} data size: ${data.size} $prevKey $nextKey"
            }
            LoadResult.Page(
                data = data,
                prevKey = prevKey,
                nextKey = nextKey,
            )
        } catch (e: Exception) {
            Napier.e(e, tag = "pagination") {
                "source load error"
            }
            LoadResult.Error(e)
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
class CustomRemoteMediator<Datum : Any>(
    private val documentStorage: Storage,
    private val collectionName: CollectionName,
    private val networkService: PagingSource<String, Datum>,
    private val update: (Datum) -> Unit,
) :
    RemoteMediator<String, Datum>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<String, Datum>,
    ): MediatorResult {
        Napier.v(tag = "pagination") {
            "mediator load $loadType"
        }
        val params = when (loadType) {
            LoadType.REFRESH -> PagingSource.LoadParams.Refresh<String>(
                null,
                state.config.pageSize,
                state.config.enablePlaceholders
            )

            LoadType.PREPEND -> {
                val remoteKey =
                    documentStorage.remoteKeyStorage.getPreRemoteKey(collectionName)?.key
                PagingSource.LoadParams.Append(
                    remoteKey
                        ?: return MediatorResult.Success(endOfPaginationReached = true),
                    state.config.pageSize,
                    state.config.enablePlaceholders
                )
            }

            LoadType.APPEND -> {
                val remoteKey =
                    documentStorage.remoteKeyStorage.getNextRemoteKey(collectionName)?.key
                PagingSource.LoadParams.Append(
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
        params: PagingSource.LoadParams<String>,
        loadType: LoadType,
    ) = when (val loadResult = networkService.load(params)) {
        is PagingSource.LoadResult.Error<String, Datum> -> {
            MediatorResult.Error(loadResult.throwable)
        }

        is PagingSource.LoadResult.Invalid<String, Datum> -> {
            MediatorResult.Error(Exception("invalid"))
        }

        is PagingSource.LoadResult.Page<String, Datum> -> {
            val data = loadResult.data
            val nextKey = loadResult.nextKey
            val preKey = loadResult.prevKey
            val collectionName = collectionName.getName()
            documentStorage.remoteKeyStorage.savePreRemoteKey(RemoteKeys(collectionName, preKey))
            documentStorage.remoteKeyStorage.saveNextRemoteKey(RemoteKeys(collectionName, nextKey))
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
    val clazz: KClass<Key>,
    val json: Json
) : PagingSource<String, T>() {

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

            is LoadParams.Refresh<*> -> LoadParams.Refresh(
                null,
                params.loadSize,
                params.placeholdersEnabled
            )
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
