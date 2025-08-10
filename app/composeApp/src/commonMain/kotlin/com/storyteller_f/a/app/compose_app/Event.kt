package com.storyteller_f.a.app.compose_app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.storyteller_f.a.app.compose_app.model.*
import com.storyteller_f.a.client.core.WebSocketClient
import com.storyteller_f.shared.model.*
import com.storyteller_f.storage.ModelCollection
import com.storyteller_f.storage.ModelStorage
import com.storyteller_f.storage.update
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableSharedFlow

val bus = MutableSharedFlow<Any>()

suspend fun processEvent(database: ModelStorage) {
    bus.collect { event ->
        when (event) {
            is OnAddReaction -> processOnAddReaction(database, event)

            is OnRemoveReaction -> processRemoveReaction(database, event)

            is OnCommunityJoined -> database.communityStorage.save(
                ModelCollection.Communities,
                event.info
            )

            is OnCommunityExited -> database.communityStorage
                .save(ModelCollection.Communities, event.info)

            is OnCommunityUpdated -> {
                database.communityStorage
                    .save(ModelCollection.Communities, event.info)
            }

            is OnTopicChanged -> processTopicChanged(event, database)

            is OnTopicCreated -> processTopicCreated(event, database)

            is OnRoomJoined -> database.roomStorage.save(ModelCollection.Rooms, event.info)

            is OnRoomExited -> database.roomStorage.save(ModelCollection.Rooms, event.info)

            is OnRoomUpdated -> {
                database.roomStorage.save(ModelCollection.Rooms, event.info)
            }

            is OnUserUpdated -> {
                database.userStorage.save(ModelCollection.Users, event.info)
            }

            is OnMediaUploaded -> {
                event.mediaInfos.forEach {
                    database.ossStorage.save(ModelCollection.Medias(it.owner), it)
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
    database.topicStorage.save(ModelCollection.TopicList(topicInfo.parentId), topicInfo)
}

private suspend fun processTopicChanged(
    event: OnTopicChanged,
    database: ModelStorage,
) {
    val topicInfo = event.topicInfo
    database.topicStorage.save(ModelCollection.TopicList(topicInfo.parentId), topicInfo)
    if (database.topicStorage.getDocument(ModelCollection.Recommend, event.topicInfo.id) != null) {
        database.topicStorage.save(ModelCollection.Recommend, topicInfo)
    }
}

private suspend fun processRemoveReaction(
    database: ModelStorage,
    event: OnRemoveReaction,
) {
    listOf(
        ModelCollection.Topics,
        ModelCollection.Recommend,
        ModelCollection.TopicList(event.topicInfo.parentId)
    ).forEach { collectionName ->
        database.topicStorage.update(collectionName, event.topicInfo.id) { old ->
            val extension = old.extension ?: TopicInfo.Extension(UserInfo.EMPTY)
            val new = extension.reactions.orEmpty().map { info ->
                if (info.emoji != event.info.emoji) {
                    info
                } else {
                    info
                }
            }.filter {
                it.count <= 0
            }.toImmutableList()
            old.copy(
                extension = extension.copy(reactions = new),
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
        ModelCollection.Topics,
        ModelCollection.Recommend,
        ModelCollection.TopicList(event.topicInfo.parentId)
    ).forEach { collectionName ->
        database.topicStorage.update(collectionName, event.info.objectId) { old ->
            val extension = old.extension ?: TopicInfo.Extension(UserInfo.EMPTY)
            val existing = extension.reactions?.firstOrNull {
                it.emoji == event.info.emoji
            }
            val new = if (existing == null) {
                val info = event.info
                extension.reactions.orEmpty().toPersistentList().add(info)
            } else {
                extension.reactions.orEmpty().map { info ->
                    if (info.emoji == event.info.emoji) {
                        event.info
                    } else {
                        info
                    }
                }.toImmutableList()
            }
            old.copy(
                extension = extension.copy(reactions = new),
                reactionCount = old.reactionCount + 1
            )
        }
    }
}

@Composable
fun TestContainer(block: @Composable () -> Unit) {
    CommonEntry("", "", {
        val appNav = remember {
            AppNav.EMPTY
        }
        CompositionLocalProvider(LocalAppNav provides appNav) {
            val ws = WebSocketClient.EMPTY
            CompositionLocalProvider(LocalWsClient provides ws) {
                block()
            }
        }
    })
}
