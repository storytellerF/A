package com.storyteller_f.a.app.common

import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.storage.ModelStorage
import com.storyteller_f.storage.TopicCollection
import com.storyteller_f.storage.update
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableSharedFlow

data class OnTopicChanged(val topicInfo: TopicInfo)
data class OnTopicCreated(val topicInfo: TopicInfo)

data class OnMediaUploaded(val fileInfos: List<FileInfo>)

data class OnUserUpdated(val info: UserInfo)

data class OnTitleCreated(val title: TitleInfo)

data class OnCommunityCreated(val info: CommunityInfo)
data class OnCommunityJoined(val info: CommunityInfo)
data class OnCommunityExited(val info: CommunityInfo)

data class OnCommunityUpdated(val info: CommunityInfo)
data class OnRoomCreated(val info: RoomInfo)
data class OnRoomJoined(val info: RoomInfo)
data class OnRoomExited(val info: RoomInfo)

data class OnRoomUpdated(val info: RoomInfo)
data class OnAddReaction(val info: ReactionInfo, val topicInfo: TopicInfo)
data class OnRemoveReaction(val info: ReactionInfo, val topicInfo: TopicInfo)

data class OnAddFavorite(val objectTuple: ObjectTuple)
data class OnRemoveFavorite(val objectTuple: ObjectTuple)

data class OnAddSubscription(val objectTuple: ObjectTuple)
data class OnRemoveSubscription(val objectTuple: ObjectTuple)

suspend fun processEvent(database: ModelStorage, bus: MutableSharedFlow<Any>) {
    bus.collect { event ->
        when (event) {
            is OnAddReaction -> processOnAddReaction(database, event)

            is OnRemoveReaction -> processRemoveReaction(database, event)

            is OnCommunityJoined -> database.community.saveToDefault(event.info)

            is OnCommunityExited -> database.community
                .saveToDefault(event.info)

            is OnCommunityUpdated -> database.community
                .saveToDefault(event.info)

            is OnTopicChanged -> processTopicChanged(event, database)

            is OnTopicCreated -> processTopicCreated(event, database)

            is OnRoomJoined -> database.room.saveToDefault(event.info)

            is OnRoomExited -> database.room.saveToDefault(event.info)

            is OnRoomUpdated -> database.room.saveToDefault(event.info)

            is OnRoomCreated -> database.room.saveToDefault(event.info)

            is OnUserUpdated -> database.user.saveToDefault(event.info)

            is OnMediaUploaded -> processOnMediaUploaded(event, database)

            is OnAddFavorite -> processOnAddFavorite(event, database)

            is OnRemoveFavorite -> processOnRemoveFavorite(event, database)

            is OnAddSubscription -> processOnAddSubscription(event, database)

            is OnRemoveSubscription -> processOnRemoveSubscriptionEvent(event, database)
        }
    }
}

private suspend fun processOnMediaUploaded(
    event: OnMediaUploaded,
    database: ModelStorage
) {
    event.fileInfos.forEach {
        database.fileInfo.saveToDefault(it)
    }
}

private suspend fun processOnAddFavorite(
    event: OnAddFavorite,
    database: ModelStorage
) {
    if (event.objectTuple.objectType == ObjectType.TOPIC) {
        database.topic.update(TopicCollection.Topics, event.objectTuple.objectId) {
            it.copy(favoriteId = -1)
        }
    }
}

private suspend fun processOnRemoveFavorite(
    event: OnRemoveFavorite,
    database: ModelStorage
) {
    if (event.objectTuple.objectType == ObjectType.TOPIC) {
        database.topic.update(
            TopicCollection.Topics,
            event.objectTuple.objectId
        ) {
            it.copy(favoriteId = null)
        }
    }
}

private suspend fun processOnAddSubscription(
    event: OnAddSubscription,
    database: ModelStorage
) {
    if (event.objectTuple.objectType == ObjectType.TOPIC) {
        database.topic.update(TopicCollection.Topics, event.objectTuple.objectId) {
            it.copy(subscriptionId = -1)
        }
    }
}

private suspend fun processOnRemoveSubscriptionEvent(
    event: OnRemoveSubscription,
    database: ModelStorage
) {
    if (event.objectTuple.objectType == ObjectType.TOPIC) {
        database.topic.update(
            TopicCollection.Topics,
            event.objectTuple.objectId
        ) {
            it.copy(subscriptionId = null)
        }
    }
}

private suspend fun processTopicCreated(
    event: OnTopicCreated,
    database: ModelStorage,
) {
    val topicInfo = event.topicInfo
    database.topic.saveToDefault(topicInfo)
}

private suspend fun processTopicChanged(
    event: OnTopicChanged,
    database: ModelStorage,
) {
    val topicInfo = event.topicInfo
    database.topic.saveToDefault(topicInfo)
    if (database.topic.getDocument(
            TopicCollection.Recommend,
            event.topicInfo.id
        ) != null
    ) {
        database.topic.saveToDefault(topicInfo)
    }
}

private suspend fun processRemoveReaction(
    database: ModelStorage,
    event: OnRemoveReaction,
) {
    listOf(
        TopicCollection.Topics,
        TopicCollection.Recommend,
        TopicCollection.ChildTopicList(event.topicInfo.parentId)
    ).forEach { collectionName ->
        database.topic.update(collectionName, event.topicInfo.id) { old ->
            val extension = old.extension ?: TopicInfo.Extension(UserInfo.EMPTY)
            val newReactions = extension.reactions.orEmpty().map { info ->
                if (info.emoji == event.info.emoji) {
                    event.info
                } else {
                    info
                }
            }.filter {
                it.count > 0
            }.toImmutableList()
            old.copy(extension = extension.copy(reactions = newReactions), reactionCount = old.reactionCount - 1)
        }
    }
}

private suspend fun processOnAddReaction(
    database: ModelStorage,
    event: OnAddReaction,
) {
    listOf(
        TopicCollection.Topics,
        TopicCollection.Recommend,
        TopicCollection.ChildTopicList(event.topicInfo.parentId)
    ).forEach { collectionName ->
        database.topic.update(collectionName, event.info.objectId) { old ->
            val extension = old.extension ?: TopicInfo.Extension(UserInfo.EMPTY)
            val oldReactions = extension.reactions.orEmpty()
            val existing = oldReactions.firstOrNull {
                it.emoji == event.info.emoji
            }
            val newReactions = if (existing == null) {
                val info = event.info
                oldReactions.toPersistentList().add(info)
            } else {
                oldReactions.map { info ->
                    if (info.emoji == event.info.emoji) {
                        event.info
                    } else {
                        info
                    }
                }.toImmutableList()
            }
            old.copy(extension = extension.copy(reactions = newReactions), reactionCount = old.reactionCount + 1)
        }
    }
}
