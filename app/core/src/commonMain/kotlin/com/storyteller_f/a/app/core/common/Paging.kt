package com.storyteller_f.a.app.core.common

import androidx.lifecycle.ViewModel
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.storyteller_f.shared.obj.Pagination
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.storage.CollectionListStorage
import com.storyteller_f.storage.RemoteKeyStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

abstract class PagingViewModel<V : Any> : ViewModel() {
    abstract val flow: Flow<PagingData<V>>
}

data class APagingData<K, T>(val data: List<T>, val pagination: K?)

@Serializable
data class SectionLoadParams(val index: Int, val param: String?)

class SectionPagingSource<DATUM : Any>(
    private val services: List<RegularPagingSource<DATUM>>
) : PagingSource<SectionLoadParams, DATUM>() {
    init {
        services.forEach {
            it.registerInvalidatedCallback {
                invalidate()
            }
        }
    }

    override suspend fun load(
        params: LoadParams<SectionLoadParams>
    ): LoadResult<SectionLoadParams, DATUM> {
        val index = params.key?.index ?: 0
        val key = params.key?.param
        return load(index, key, params.loadSize, params.placeholdersEnabled)
    }

    private suspend fun load(
        index: Int,
        key: String?,
        loadSize: Int,
        placeholdersEnabled: Boolean
    ): LoadResult<SectionLoadParams, DATUM> {
        val service = services.getOrNull(index) ?: return LoadResult.Page(
            data = emptyList(), prevKey = null, // Only paging forward.
            nextKey = null
        )
        val pageResult = service.load(
            if (key == null) {
                LoadParams.Refresh(null, loadSize, placeholdersEnabled)
            } else {
                LoadParams.Append(key, loadSize, placeholdersEnabled)
            }
        )
        return when (pageResult) {
            is LoadResult.Error<String, DATUM> -> LoadResult.Error(pageResult.throwable)
            is LoadResult.Invalid<String, DATUM> -> LoadResult.Invalid()
            is LoadResult.Page<String, DATUM> -> load(
                loadSize,
                pageResult,
                index,
                placeholdersEnabled
            )
        }
    }

    private suspend fun load(
        loadSize: Int,
        prePageResult: LoadResult.Page<String, DATUM>,
        index: Int,
        placeholdersEnabled: Boolean
    ): LoadResult<SectionLoadParams, DATUM> {
        return if (loadSize - prePageResult.data.size > 0) {
            getNextParams(prePageResult.nextKey, index)?.let {
                when (val nextPageResult =
                    load(it.index, it.param, loadSize, placeholdersEnabled)) {
                    is LoadResult.Error<SectionLoadParams, DATUM> ->
                        LoadResult.Error(nextPageResult.throwable)

                    is LoadResult.Invalid<SectionLoadParams, DATUM> ->
                        LoadResult.Invalid()

                    is LoadResult.Page<SectionLoadParams, DATUM> -> {
                        // 合并两页
                        LoadResult.Page(
                            prePageResult.data + nextPageResult.data,
                            null,
                            nextPageResult.nextKey
                        )
                    }
                }
            } ?: LoadResult.Page(
                prePageResult.data,
                null,
                getNextParams(prePageResult.nextKey, index)
            )
        } else {
            LoadResult.Page(
                prePageResult.data,
                null,
                getNextParams(prePageResult.nextKey, index)
            )
        }
    }

    private fun getNextParams(
        nextToken: String?,
        index: Int
    ): SectionLoadParams? =
        if (nextToken != null) {
            SectionLoadParams(index, nextToken)
        } else {
            val newIndex = index + 1
            if (newIndex >= services.size) {
                null
            } else {
                SectionLoadParams(newIndex, null)
            }
        }

    override fun getRefreshKey(state: PagingState<SectionLoadParams, DATUM>) = null
}

class RegularPagingSource<DATUM : Any>(
    val service: suspend (String?, Int) -> Result<ServerResponse<DATUM>>
) : PagingSource<String, DATUM>() {
    override suspend fun load(params: LoadParams<String>): LoadResult<String, DATUM> {
        return service(params.key, params.loadSize).map {
            val pagination = it.pagination
            APagingData(
                it.data,
                Pagination(
                    pagination?.nextPageToken,
                    pagination?.prePageToken,
                    pagination?.total ?: 0
                )
            )
        }.fold(onSuccess = { (data, pagination) ->
            LoadResult.Page(
                data = data,
                prevKey = null,
                nextKey = pagination?.nextPageToken
            )
        }, onFailure = {
            LoadResult.Error(it)
        })
    }

    override fun getRefreshKey(state: PagingState<String, DATUM>): String? {
        return null
    }
}

@OptIn(ExperimentalPagingApi::class)
fun <C : Any, T : Any> buildPager(
    collection: C,
    collectionName: String,
    remoteKeyStorage: RemoteKeyStorage,
    storage: CollectionListStorage<C, T>,
    service: suspend (String?, Int) -> Result<ServerResponse<T>>
): Pager<String, T> = Pager(
    PagingConfig(pageSize = 20),
    remoteMediator = CustomRemoteMediator(
        collectionName,
        remoteKeyStorage,
        RegularPagingSource { key, size ->
            service(key, size)
        },
    ) { data, clean ->
        if (clean) {
            storage.clean(collection)
        }
        data.forEach {
            storage.save(collection, it)
        }
    },
) {
    CompatPagingSource(storage.observeData(collection), IntKeyConverter)
}
