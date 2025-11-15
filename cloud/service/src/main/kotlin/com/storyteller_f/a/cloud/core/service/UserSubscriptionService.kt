package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.api.NewSubscription
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.addIfNotExists
import com.storyteller_f.a.backend.core.types.UserSubscription
import com.storyteller_f.a.backend.core.types.toUserSubscriptionInfo
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.model.UserSubscriptionInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.firstOrNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now

suspend fun Backend.addSubscription(
    uid: PrimaryKey,
    newSubscription: NewSubscription
) = addIfNotExists({
    database.subscription.getSubscription(uid, newSubscription.objectId)
}, {
    val id = SnowflakeFactory.nextId()
    val userSubscription = UserSubscription(
        id,
        uid,
        newSubscription.objectId,
        newSubscription.objectType,
        now()
    )
    database.subscription.addSubscription(userSubscription).onSuccess {
        addUserLog(uid, UserLogType.ADD_SUBSCRIPTION, newSubscription.tuple())
    }
}).mapResultIfNotNull {
    processUserSubscriptionToUserSubscriptionInfo(uid, listOf(it))
}.firstOrNull()

suspend fun Backend.removeSubscription(
    uid: PrimaryKey,
    subscriptionId: PrimaryKey
) = database.subscription.getSubscription(subscriptionId)
    .mapResultIfNotNull { subscription ->
        if (subscription.uid == uid) {
            database.subscription.removeSubscription(subscriptionId).onSuccess {
                addUserLog(uid, UserLogType.REMOVE_SUBSCRIPTION, subscription.objectTuple())
            }
        } else {
            Result.failure(ForbiddenException("No permission to remove this subscription"))
        }
    }

suspend fun Backend.getUserSubscriptions(
    uid: PrimaryKey,
    fetch: PrimaryKeyFetch
) = database.subscription.getUserSubscriptions(uid, fetch)
    .mapResult { paginationResult ->
        processUserSubscriptionToUserSubscriptionInfo(uid, paginationResult.list).map { list ->
            PaginationResult(list, paginationResult.total)
        }
    }

suspend fun Backend.processUserSubscriptionToUserSubscriptionInfo(
    uid: PrimaryKey,
    userSubscriptions: List<UserSubscription>
) = runCatching {
    userSubscriptions.map {
        val favoriteInfo = it.toUserSubscriptionInfo()
        when (it.objectType) {
            ObjectType.TOPIC -> {
                val topicInfo = getTopic(it.objectId, uid, true).getOrThrow()
                favoriteInfo.copy(extensions = UserSubscriptionInfo.Extensions(topicInfo))
            }

            else -> favoriteInfo
        }
    }
}
