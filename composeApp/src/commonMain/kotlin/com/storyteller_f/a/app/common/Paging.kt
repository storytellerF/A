package com.storyteller_f.a.app.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import app.cash.paging.*
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKeyOrNull
import io.ktor.client.*

@OptIn(ExperimentalPagingApi::class)
abstract class PagingViewModel<K : Any, V : Any>(
    sourceBuilder: () -> PagingSource<K, V>,
    remoteMediator: RemoteMediator<K, V>? = null,
    val client: HttpClient
) :
    ViewModel() {
    val flow = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = remoteMediator,
    ) {
        sourceBuilder()
    }.flow
        .cachedIn(viewModelScope)
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

class RegularPagingSource<DATUM : Any>(
    val client: HttpClient,
    val service: suspend HttpClient.(PrimaryKey?) -> Result<ServerResponse<DATUM>>
) :
    PagingSource<PrimaryKey, DATUM>() {
    override suspend fun load(params: LoadParams<PrimaryKey>): PagingSourceLoadResult<PrimaryKey, DATUM> {
        return client.service(params.key).map {
            APagingData(it.data, it.pagination?.nextPageToken?.toPrimaryKeyOrNull())
        }.loadResult()
    }

    override fun getRefreshKey(state: PagingState<PrimaryKey, DATUM>): PrimaryKey? {
        return null
    }
}
