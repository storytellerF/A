package com.storyteller_f.types

import com.storyteller_f.shared.type.PrimaryKey

data class PaginationResult<T>(val list: List<T>, val total: Long)

interface Fetch {
    val size: Int
}

data class PagingFetch(val pre: PrimaryKey?, val next: PrimaryKey?, override val size: Int) : Fetch
