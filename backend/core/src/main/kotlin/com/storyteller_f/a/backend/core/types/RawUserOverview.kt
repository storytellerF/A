package com.storyteller_f.a.backend.core.types

data class RawUserOverview(
    val subscriptionCount: Long,
    val favoriteCount: Long,
    val acg: Long,
    val childAccountCount: Long,
    val rawUser: RawUser
)
