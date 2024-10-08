package com.storyteller_f.shared.obj

import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.type.OKey
import com.storyteller_f.shared.type.ObjectType
import kotlinx.serialization.Serializable

@Serializable
data class NewTopic(val parentType: ObjectType, val parentId: OKey, val content: TopicContent)
