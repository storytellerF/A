/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class UserSubscriptionItem(val subscriptionInfo: UserSubscriptionInfo, val topicInfo: TopicDetail? = null) :
    PrimaryKeyIdentifiable {
    override val id: PrimaryKey get() = subscriptionInfo.id
    val uid: PrimaryKey get() = subscriptionInfo.uid
    val objectId: PrimaryKey get() = subscriptionInfo.objectId
    val objectType: ObjectType get() = subscriptionInfo.objectType
    val createdTime: LocalDateTime get() = subscriptionInfo.createdTime
    companion object {
        val EMPTY = UserSubscriptionItem(UserSubscriptionInfo.EMPTY)
    }
}
