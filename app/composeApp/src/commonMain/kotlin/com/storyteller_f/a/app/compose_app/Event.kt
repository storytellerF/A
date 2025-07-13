package com.storyteller_f.a.app.compose_app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.storyteller_f.a.app.compose_app.model.*
import com.storyteller_f.a.client.core.WebSocketClient
import com.storyteller_f.shared.model.*
import com.storyteller_f.storage.CollectionName
import com.storyteller_f.storage.DocumentStorage
import com.storyteller_f.storage.update
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableSharedFlow

val bus = MutableSharedFlow<Any>()

suspend fun processEvent(database: DocumentStorage) {
    bus.collect { event ->
        when (event) {
            is OnAddReaction -> processOnAddReaction(database, event)

            is OnRemoveReaction -> processRemoveReaction(database, event)

            is OnCommunityJoined -> database.communityStorage.save(
                CollectionName.Communities,
                event.info
            )

            is OnCommunityExited -> database.communityStorage
                .save(CollectionName.Communities, event.info)

            is OnCommunityUpdated -> {
                database.communityStorage
                    .save(CollectionName.Communities, event.info)
            }

            is OnTopicChanged -> processTopicChanged(event, database)

            is OnTopicCreated -> processTopicCreated(event, database)

            is OnRoomJoined -> database.roomStorage.save(CollectionName.Rooms, event.info)

            is OnRoomExited -> database.roomStorage.save(CollectionName.Rooms, event.info)

            is OnRoomUpdated -> {
                database.roomStorage.save(CollectionName.Rooms, event.info)
            }

            is OnUserUpdated -> {
                database.userStorage.save(CollectionName.Users, event.info)
            }

            is OnMediaUploaded -> {
                event.mediaInfos.forEach {
                    database.mediasStorage.save(CollectionName.Medias(it.owner), it)
                }
            }
        }
    }
}

private fun processTopicCreated(
    event: OnTopicCreated,
    database: DocumentStorage,
) {
    val topicInfo = event.topicInfo
    database.topicStorage.save(CollectionName.TopicList(topicInfo.parentId), topicInfo)
}

private fun processTopicChanged(
    event: OnTopicChanged,
    database: DocumentStorage,
) {
    val topicInfo = event.topicInfo
    database.topicStorage.save(CollectionName.TopicList(topicInfo.parentId), topicInfo)
    if (database.topicStorage.getDocument(CollectionName.Recommend, event.topicInfo.id) != null) {
        database.topicStorage.save(CollectionName.Recommend, topicInfo)
    }
}

private fun processRemoveReaction(
    database: DocumentStorage,
    event: OnRemoveReaction,
) {
    listOf(
        CollectionName.Topics,
        CollectionName.Recommend,
        CollectionName.TopicList(event.topicInfo.parentId)
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

private fun processOnAddReaction(
    database: DocumentStorage,
    event: OnAddReaction,
) {
    listOf(
        CollectionName.Topics,
        CollectionName.Recommend,
        CollectionName.TopicList(event.topicInfo.parentId)
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
