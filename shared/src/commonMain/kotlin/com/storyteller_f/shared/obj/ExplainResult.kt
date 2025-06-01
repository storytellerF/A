package com.storyteller_f.shared.obj

import kotlinx.serialization.Serializable

@Serializable
data class ExplainResult(val dialect: String, val sql: String, val result: String, val stackTraceString: String)
