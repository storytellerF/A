package com.storyteller_f.a.app.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import app.cash.paging.*
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKeyOrNull
import io.ktor.client.*
import kotlinx.serialization.Serializable

@OptIn(ExperimentalPagingApi::class)
abstract class PagingViewModel<K : Any, V : Any>(
    val client: HttpClient,
    remoteMediator: RemoteMediator<K, V>? = null,
    sourceBuilder: () -> PagingSource<K, V>
) : ViewModel() {
    val flow = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = remoteMediator,
    ) {
        sourceBuilder()
    }.flow.cachedIn(viewModelScope)
}

fun <KEY : Any, DATUM : Any> Result<APagingData<KEY, DATUM>>.loadResult(): PagingSourceLoadResult<KEY, DATUM> {
    return fold(onSuccess = { (data, nextKey) ->
        PagingSourceLoadResultPage(
            data = data,
            prevKey = null, // Only paging forward.
            nextKey = nextKey
        )
    }, onFailure = {
        PagingSourceLoadResultError(it)
    })
}

data class APagingData<K, T>(val data: List<T>, val pagination: K?)

@Serializable
data class SectionLoadParams<Param : Any>(val index: Int, val param: Param?)

class SectionPagingSource<DATUM : Any>(
    private val services: List<RegularPagingSource<DATUM>>
) : PagingSource<SectionLoadParams<PrimaryKey>, DATUM>() {
    override suspend fun load(
        params: LoadParams<SectionLoadParams<PrimaryKey>>
    ): PagingSourceLoadResult<SectionLoadParams<PrimaryKey>, DATUM> {
        val index = params.key?.index ?: 0
        val key = params.key?.param
        return load(index, key, params.loadSize, params.placeholdersEnabled)
    }

    private suspend fun load(
        index: Int,
        key: PrimaryKey?,
        loadSize: Int,
        placeholdersEnabled: Boolean
    ): LoadResult<SectionLoadParams<PrimaryKey>, DATUM> {
        val service = services.getOrNull(index) ?: return PagingSourceLoadResultPage(
            data = emptyList(), prevKey = null, // Only paging forward.
            nextKey = null
        )
        val pageResult = service.load(
            if (key == null) {
                PagingSourceLoadParamsRefresh(null, loadSize, placeholdersEnabled)
            } else {
                PagingSourceLoadParamsAppend(key, loadSize, placeholdersEnabled)
            }
        )
        return when (pageResult) {
            is PagingSourceLoadResultError<PrimaryKey, DATUM> -> PagingSourceLoadResultError(pageResult.throwable)
            is PagingSourceLoadResultInvalid<PrimaryKey, DATUM> -> PagingSourceLoadResultInvalid()
            is PagingSourceLoadResultPage<PrimaryKey, DATUM> -> load(loadSize, pageResult, index, placeholdersEnabled)
        }
    }

    private suspend fun load(
        loadSize: Int,
        prePageResult: PagingSourceLoadResultPage<PrimaryKey, DATUM>,
        index: Int,
        placeholdersEnabled: Boolean
    ): LoadResult<SectionLoadParams<PrimaryKey>, DATUM> {
        return if (loadSize - prePageResult.data.size > 0) {
            getNextParams(prePageResult.nextKey, index)?.let {
                when (val nextPageResult = load(it.index, it.param, loadSize, placeholdersEnabled)) {
                    is PagingSourceLoadResultError<SectionLoadParams<PrimaryKey>, DATUM> ->
                        PagingSourceLoadResultError(nextPageResult.throwable)

                    is PagingSourceLoadResultInvalid<SectionLoadParams<PrimaryKey>, DATUM> ->
                        PagingSourceLoadResultInvalid()

                    is PagingSourceLoadResultPage<SectionLoadParams<PrimaryKey>, DATUM> -> {
                        // 合并两页
                        PagingSourceLoadResultPage(
                            prePageResult.data + nextPageResult.data,
                            null,
                            nextPageResult.nextKey
                        )
                    }
                }
            } ?: PagingSourceLoadResultPage(
                prePageResult.data,
                null,
                getNextParams(prePageResult.nextKey, index)
            )
        } else {
            PagingSourceLoadResultPage(
                prePageResult.data,
                null,
                getNextParams(prePageResult.nextKey, index)
            )
        }
    }

    private fun getNextParams(
        nextToken: PrimaryKey?,
        index: Int
    ): SectionLoadParams<PrimaryKey>? =
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

    override fun getRefreshKey(state: PagingState<SectionLoadParams<PrimaryKey>, DATUM>) = null
}

class RegularPagingSource<DATUM : Any>(
    val client: HttpClient,
    val service: suspend HttpClient.(PrimaryKey?, Int) -> Result<ServerResponse<DATUM>>
) : PagingSource<PrimaryKey, DATUM>() {
    override suspend fun load(params: LoadParams<PrimaryKey>): PagingSourceLoadResult<PrimaryKey, DATUM> {
        return client.service(params.key, params.loadSize).map {
            APagingData(it.data, it.pagination?.nextPageToken?.toPrimaryKeyOrNull())
        }.loadResult()
    }

    override fun getRefreshKey(state: PagingState<PrimaryKey, DATUM>): PrimaryKey? {
        return null
    }
}
