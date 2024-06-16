package com.storyteller_f.a.app.common

import androidx.paging.ExperimentalPagingApi
import app.cash.paging.*
import io.github.aakira.napier.Napier
import moe.tlaster.precompose.viewmodel.ViewModel
import moe.tlaster.precompose.viewmodel.viewModelScope

@OptIn(ExperimentalPagingApi::class)
abstract class PagingViewModel<K : Any, V : Any>(
    sourceBuilder: PagingViewModel<K, V>.() -> PagingSource<K, V>,
    remoteMediator: RemoteMediator<K, V>? = null,
) :
    ViewModel() {
    val flow = Pager(
        PagingConfig(pageSize = 20), remoteMediator = remoteMediator,
    ) {
        sourceBuilder()
    }.flow
        .cachedIn(viewModelScope)
}

inline fun <R> serviceCatching(block: () -> R): Result<R> {
    return try {
        val value = block()
        Result.success(value)
    } catch (e: Throwable) {
        Napier.e(e) {
            "serviceCatching"
        }
        Result.failure(e)
    }
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

class SimplePagingSource<KEY : Any, DATUM : Any>(val service: suspend (KEY?) -> Result<APagingData<KEY, DATUM>>) :
    PagingSource<KEY, DATUM>() {
    override suspend fun load(params: LoadParams<KEY>): PagingSourceLoadResult<KEY, DATUM> {
        return service(params.key).loadResult()
    }


    override fun getRefreshKey(state: PagingState<KEY, DATUM>): KEY? {
        return null
    }

}
