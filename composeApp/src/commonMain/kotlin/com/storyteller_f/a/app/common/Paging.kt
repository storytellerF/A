package com.storyteller_f.a.app.common

import androidx.lifecycle.ViewModel
import app.cash.paging.*
import com.storyteller_f.a.client_lib.SessionManager
import com.storyteller_f.shared.obj.Pagination
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKeyOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

abstract class PagingViewModel<K : Any, V : Any> : ViewModel() {
    abstract val flow: Flow<PagingData<V>>
}

data class APagingData<K, T>(val data: List<T>, val pagination: K?)

@Serializable
data class SectionLoadParams(val index: Int, val param: String?)



class SectionPagingSource<DATUM : Any>(
    private val services: List<RegularPagingSource<DATUM>>
) : PagingSource<SectionLoadParams, DATUM>() {
    override suspend fun load(
        params: LoadParams<SectionLoadParams>
    ): PagingSourceLoadResult<SectionLoadParams, DATUM> {
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
            is PagingSourceLoadResultError<String, DATUM> -> PagingSourceLoadResultError(pageResult.throwable)
            is PagingSourceLoadResultInvalid<String, DATUM> -> PagingSourceLoadResultInvalid()
            is PagingSourceLoadResultPage<String, DATUM> -> load(loadSize, pageResult, index, placeholdersEnabled)
        }
    }

    private suspend fun load(
        loadSize: Int,
        prePageResult: PagingSourceLoadResultPage<String, DATUM>,
        index: Int,
        placeholdersEnabled: Boolean
    ): LoadResult<SectionLoadParams, DATUM> {
        return if (loadSize - prePageResult.data.size > 0) {
            getNextParams(prePageResult.nextKey, index)?.let {
                when (val nextPageResult = load(it.index, it.param, loadSize, placeholdersEnabled)) {
                    is PagingSourceLoadResultError<SectionLoadParams, DATUM> ->
                        PagingSourceLoadResultError(nextPageResult.throwable)

                    is PagingSourceLoadResultInvalid<SectionLoadParams, DATUM> ->
                        PagingSourceLoadResultInvalid()

                    is PagingSourceLoadResultPage<SectionLoadParams, DATUM> -> {
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
    val sessionManager: SessionManager,
    val service: suspend SessionManager.(String?, Int) -> Result<ServerResponse<DATUM>>
) : PagingSource<String, DATUM>() {
    override suspend fun load(params: LoadParams<String>): PagingSourceLoadResult<String, DATUM> {
        return sessionManager.service(params.key, params.loadSize).map {
            APagingData(
                it.data,
                Pagination(
                    it.pagination?.nextPageToken,
                    it.pagination?.prePageToken,
                    0
                )
            )
        }.fold(onSuccess = { (data, pagination) ->
            PagingSourceLoadResultPage(
                data = data,
                prevKey = null,
                nextKey = pagination?.nextPageToken
            )
        }, onFailure = {
            PagingSourceLoadResultError(it)
        })
    }

    override fun getRefreshKey(state: PagingState<String, DATUM>): String? {
        return null
    }
}
