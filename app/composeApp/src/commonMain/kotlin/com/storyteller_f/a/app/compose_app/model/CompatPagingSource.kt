package com.storyteller_f.a.app.compose_app.model

import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import androidx.paging.PagingState

class CompatPagingSource<OK : Any, TK : Any, T : Any>(
    val rawSource: PagingSource<OK, T>,
    val converter: CompatKeyConverter<OK, TK>
) :
    PagingSource<TK, T>() {
    init {
        rawSource.registerInvalidatedCallback {
            invalidate()
        }
    }

    override fun getRefreshKey(state: PagingState<TK, T>): TK? {
        return null
    }

    override suspend fun load(params: LoadParams<TK>): LoadResult<TK, T> {
        val loadResult = rawSource.load(
            when (params) {
                is LoadParams.Append<TK> -> LoadParams.Append(
                    converter.to(params.key),
                    params.loadSize,
                    params.placeholdersEnabled
                )

                is LoadParams.Prepend<TK> -> LoadParams.Prepend(
                    converter.to(params.key),
                    params.loadSize,
                    params.placeholdersEnabled
                )

                is LoadParams.Refresh<TK> -> LoadParams.Refresh(
                    params.key?.let { converter.to(it) },
                    params.loadSize,
                    params.placeholdersEnabled
                )
            }
        )
        return when (loadResult) {
            is LoadResult.Error<OK, T> -> LoadResult.Error(loadResult.throwable)
            is LoadResult.Invalid<OK, T> -> LoadResult.Invalid()
            is LoadResult.Page<OK, T> -> LoadResult.Page(
                loadResult.data,
                loadResult.prevKey?.let { converter.from(it) },
                loadResult.nextKey?.let { converter.from(it) }
            )
        }
    }
}
