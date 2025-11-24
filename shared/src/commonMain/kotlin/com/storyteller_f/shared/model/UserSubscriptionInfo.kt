package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.DEFAULT_PRIMARY_KEY
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.now
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class UserSubscriptionInfo(
    override val id: PrimaryKey,
    val uid: PrimaryKey,
    val objectId: PrimaryKey,
    val objectType: ObjectType,
    val createdTime: LocalDateTime,
    val extensions: Extensions? = null
) : PrimaryKeyIdentifiable {
    @Serializable
    data class Extensions(val topicInfo: TopicInfo? = null)

    companion object {
        val EMPTY = UserSubscriptionInfo(
            id = DEFAULT_PRIMARY_KEY,
            uid = DEFAULT_PRIMARY_KEY,
            objectId = DEFAULT_PRIMARY_KEY,
            objectType = ObjectType.TOPIC,
            now(),
            null
        )
    }
}
