package com.storyteller_f.shared.obj

import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class UpdateUserBody(val nickname: String? = null, val avatar: PrimaryKey? = null, val aid: String? = null)

@Serializable
data class UpdateUserRead(val objectTuple: ObjectTuple, val topicId: PrimaryKey)


@Serializable
data class UpdateCommunityBody(val name: String? = null, val icon: PrimaryKey? = null, val poster: PrimaryKey? = null)

@Serializable
data class UpdateRoomBody(val name: String? = null, val icon: PrimaryKey? = null)
