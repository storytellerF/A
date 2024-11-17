package com.storyteller_f.types

data class PaginationResult<T>(val list: List<T>, val total: Long)
