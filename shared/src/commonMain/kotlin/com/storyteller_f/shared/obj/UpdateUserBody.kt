package com.storyteller_f.shared.obj

import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class UpdateUserBody(val nickname: String? = null, val avatar: String? = null, val aid: String? = null)

@Serializable
data class UpdateUserRead(val objectTuple: ObjectTuple, val topicId: PrimaryKey)
