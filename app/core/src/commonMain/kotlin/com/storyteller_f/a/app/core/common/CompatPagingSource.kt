package com.storyteller_f.a.app.core.common

import androidx.paging.PagingSource
import androidx.paging.PagingState

interface CompatKeyConverter<F, T> {
    fun from(f: F): T
    fun to(t: T): F
}

object IntKeyConverter : CompatKeyConverter<Int, String> {
    override fun from(f: Int): String {
        return f.toString()
    }

    override fun to(t: String): Int {
        return t.toInt()
    }
}

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
