package com.storyteller_f.shared.obj

import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class NewTopic(val parentType: ObjectType, val parentId: PrimaryKey, val content: TopicContent)
