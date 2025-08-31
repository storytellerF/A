package com.storyteller_f.a.app.compose_app

import com.storyteller_f.a.app.compose_app.model.*
import com.storyteller_f.shared.model.*
import com.storyteller_f.storage.CommunityCollection
import com.storyteller_f.storage.MediasCollection
import com.storyteller_f.storage.ModelStorage
import com.storyteller_f.storage.RoomCollection
import com.storyteller_f.storage.TopicCollection
import com.storyteller_f.storage.UserCollection
import com.storyteller_f.storage.update
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableSharedFlow

val bus = MutableSharedFlow<Any>()

suspend fun processEvent(database: ModelStorage, bus: MutableSharedFlow<Any>) {
    bus.collect { event ->
        when (event) {
            is OnAddReaction -> processOnAddReaction(database, event)

            is OnRemoveReaction -> processRemoveReaction(database, event)

            is OnCommunityJoined -> database.communityInfoStorage.save(
                CommunityCollection.Communities,
                event.info
            )

            is OnCommunityExited -> database.communityInfoStorage
                .save(CommunityCollection.Communities, event.info)

            is OnCommunityUpdated -> {
                database.communityInfoStorage
                    .save(CommunityCollection.Communities, event.info)
            }

            is OnTopicChanged -> processTopicChanged(event, database)

            is OnTopicCreated -> processTopicCreated(event, database)

            is OnRoomJoined -> database.roomInfoStorage.save(RoomCollection.Rooms, event.info)

            is OnRoomExited -> database.roomInfoStorage.save(RoomCollection.Rooms, event.info)

            is OnRoomUpdated -> {
                database.roomInfoStorage.save(RoomCollection.Rooms, event.info)
            }

            is OnUserUpdated -> {
                database.userInfoStorage.save(UserCollection.Users, event.info)
            }

            is OnMediaUploaded -> {
                event.fileInfos.forEach {
                    database.fileInfoStorage.save(MediasCollection(it.owner), it)
                }
            }
        }
    }
}

private suspend fun processTopicCreated(
    event: OnTopicCreated,
    database: ModelStorage,
) {
    val topicInfo = event.topicInfo
    database.topicInfoStorage.save(TopicCollection.TopicList(topicInfo.parentId), topicInfo)
}

private suspend fun processTopicChanged(
    event: OnTopicChanged,
    database: ModelStorage,
) {
    val topicInfo = event.topicInfo
    database.topicInfoStorage.save(TopicCollection.TopicList(topicInfo.parentId), topicInfo)
    if (database.topicInfoStorage.getDocument(TopicCollection.Recommend, event.topicInfo.id) != null) {
        database.topicInfoStorage.save(TopicCollection.Recommend, topicInfo)
    }
}

private suspend fun processRemoveReaction(
    database: ModelStorage,
    event: OnRemoveReaction,
) {
    listOf(
        TopicCollection.Topics,
        TopicCollection.Recommend,
        TopicCollection.TopicList(event.topicInfo.parentId)
    ).forEach { collectionName ->
        database.topicInfoStorage.update(collectionName, event.topicInfo.id) { old ->
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
            old.copy(
                extension = extension.copy(reactions = newReactions),
                reactionCount = old.reactionCount - 1
            )
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
        TopicCollection.TopicList(event.topicInfo.parentId)
    ).forEach { collectionName ->
        database.topicInfoStorage.update(collectionName, event.info.objectId) { old ->
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
            old.copy(
                extension = extension.copy(reactions = newReactions),
                reactionCount = old.reactionCount + 1
            )
        }
    }
}
