package com.storyteller_f.a.backend.core

import com.storyteller_f.a.backend.core.types.RawTopic
import com.storyteller_f.a.backend.core.types.RawUserOverview
import com.storyteller_f.a.backend.core.types.Topic
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicPinSearch
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.associateByPair
import com.storyteller_f.shared.utils.firstOrNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull

/** Assembles a full [RawUserOverview] for the given user. */
suspend fun CombinedDatabase.getUserOverview(uid: PrimaryKey): Result<RawUserOverview> = runCatching {
    val subscriptionCount = subscription.getUserSubscriptionCount(uid).getOrThrow()
    val favoriteCount = favorite.getUserFavoriteCount().getOrThrow()
    val childAccountCount = user.getChildAccountCount(uid).getOrThrow()
    val reactionRecordCount = reaction.getUserReactionRecordCount(uid).getOrThrow()
    val commentCount = topic.getUserCommentCount(uid).getOrThrow()
    val childAccountIds = user.getChildAccountIds(uid).getOrThrow()
    val hasUnreadChildRoomMessage = if (childAccountIds.isEmpty()) {
        false
    } else {
        container.getUsersHasUnreadRoomMap(childAccountIds).getOrThrow().values.any { it }
    }
    val rawUser = user.getRawUser(ObjectFetch.IdFetch(uid), uid).getOrThrow() ?: error("user not found")
    RawUserOverview(
        subscriptionCount,
        favoriteCount,
        rawUser.user.acgAmount,
        childAccountCount,
        reactionRecordCount,
        commentCount,
        hasUnreadChildRoomMessage,
        rawUser
    )
}

/** Enriches raw [Topic] objects with content, counts, and user state. */
suspend fun CombinedDatabase.processTopicToRawTopic(
    uid: PrimaryKey?,
    topics: List<Topic>
): Result<List<RawTopic>> = runCatching {
    val topicIds = topics.map { it.id }
    if (topicIds.isEmpty()) {
        emptyList()
    } else {
        val commentedSet = if (uid != null) {
            topic.isUserCommented(uid, topicIds).map { it.toSet() }.getOrThrow()
        } else {
            emptySet()
        }

        val commentCountMap = topic.getTopicCommentCount(topicIds).map { it.associateByPair() }.getOrThrow()
        val reactionCountMap = reaction.getReactionCount(topicIds).map { it.associateByPair() }.getOrThrow()

        val lastReadMap = if (uid != null) {
            container.getTopicReadList(topicIds, uid)
                .map { it.associateBy { userTopicRead -> userTopicRead.objectId } }
                .getOrThrow()
        } else {
            emptyMap()
        }

        val contentMap = topic.getTopicContentFromByteArray(topics, uid).getOrThrow()

        val favoriteMap = if (uid != null) {
            favorite.getHasFavorite(ObjectListFetch.IdListFetch(topicIds), uid)
                .getOrThrow()
                .associateBy { it.objectId }
        } else {
            emptyMap()
        }

        val subscriptionMap = if (uid != null) {
            subscription.getHasSubscription(ObjectListFetch.IdListFetch(topicIds), uid)
                .getOrThrow()
                .associateBy { it.objectId }
        } else {
            emptyMap()
        }

        topics.map { topic ->
            val id = topic.id
            RawTopic(
                topic,
                contentMap[id] ?: TopicContent.Nil,
                commentCountMap[id] ?: 0,
                commentedSet.contains(id),
                reactionCountMap[id] ?: 0,
                lastReadMap[id]?.topicId,
                favoriteId = favoriteMap[id]?.id,
                subscriptionId = subscriptionMap[id]?.id,
            )
        }
    }
}

/** Returns a single enriched topic or null when not found. */
suspend fun CombinedDatabase.getRawTopic(fetch: ObjectFetch, uid: PrimaryKey?) =
    topic.getTopic(fetch).mapResultIfNotNull { topic ->
        processTopicToRawTopic(uid, listOf(topic))
    }.firstOrNull()

/** Returns a paginated list of enriched topics. */
suspend fun CombinedDatabase.getAllRawTopics(primaryKeyFetch: PrimaryKeyFetch): Result<PaginationResult<RawTopic>> {
    return topic.getAllTopicPagination(primaryKeyFetch).mapResult {
        processTopicToRawTopic(null, it.list).pagingNotNull(it.total)
    }
}

/** Returns enriched topics for the given [ids]. */
suspend fun CombinedDatabase.getRawTopicListByIds(uid: PrimaryKey?, ids: List<PrimaryKey>) =
    topic.getTopicListByIds(ids).mapResult {
        processTopicToRawTopic(uid, it)
    }

/** Returns paginated child topics for a parent, enriched with user state. */
suspend fun CombinedDatabase.getRawTopicByParentId(
    uid: PrimaryKey?,
    primaryKeyFetch: PrimaryKeyFetch,
    parentId: PrimaryKey,
    pinType: TopicPinSearch?
) = topic.getTopicByParentId(uid, primaryKeyFetch, parentId, pinType).mapResult {
    processTopicToRawTopic(uid, it.list).pagingNotNull(it.total)
}

/** Returns the most recent child topic for a parent, enriched with user state. */
suspend fun CombinedDatabase.getLatestRawTopic(uid: PrimaryKey?, parentId: PrimaryKey) =
    topic.getLatestTopic(parentId).mapResult {
        processTopicToRawTopic(uid, it)
    }
