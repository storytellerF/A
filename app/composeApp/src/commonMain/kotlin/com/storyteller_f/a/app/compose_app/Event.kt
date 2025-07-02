package com.storyteller_f.a.app.compose_app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.storyteller_f.a.app.compose_app.model.*
import com.storyteller_f.a.client.core.WebSocketClient
import com.storyteller_f.shared.model.*
import com.storyteller_f.storage.StorageExpression
import com.storyteller_f.storage.StorageSource
import com.storyteller_f.storage.save
import com.storyteller_f.storage.update
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableSharedFlow

val bus = MutableSharedFlow<Any>()

suspend fun processEvent(database: StorageSource) {
    bus.collect { event ->
        when (event) {
            is com.storyteller_f.a.app.compose_app.model.OnAddReaction -> processOnAddReaction(database, event)

            is com.storyteller_f.a.app.compose_app.model.OnRemoveReaction -> processRemoveReaction(database, event)

            is com.storyteller_f.a.app.compose_app.model.OnCommunityJoined -> database.getCollection(
                "communities",
                CommunityInfo::class
            )
                .save(event.info.id, event.info)

            is com.storyteller_f.a.app.compose_app.model.OnCommunityExited -> database.getCollection(
                "communities",
                CommunityInfo::class
            )
                .save(event.info.id, event.info)

            is com.storyteller_f.a.app.compose_app.model.OnCommunityUpdated -> {
                database.getCollection("communities", CommunityInfo::class).save(event.info.id, event.info)
                database.getCollectionByPrefix("communities_", CommunityInfo::class).filter {
                    it.exists(StorageExpression.IdEq("id", event.info.id))
                }.forEach {
                    it.save(event.info.id, event.info)
                }
            }

            is com.storyteller_f.a.app.compose_app.model.OnTopicChanged -> processTopicChanged(event, database)

            is com.storyteller_f.a.app.compose_app.model.OnTopicCreated -> processTopicCreated(event, database)

            is com.storyteller_f.a.app.compose_app.model.OnRoomJoined -> database.getCollection(
                "rooms",
                RoomInfo::class
            ).save(event.info.id, event.info)

            is com.storyteller_f.a.app.compose_app.model.OnRoomExited -> database.getCollection(
                "rooms",
                RoomInfo::class
            ).save(event.info.id, event.info)

            is com.storyteller_f.a.app.compose_app.model.OnRoomUpdated -> {
                database.getCollection("rooms", RoomInfo::class).save(event.info.id, event.info)
                database.getCollectionByPrefix("rooms_", RoomInfo::class).filter {
                    it.exists(StorageExpression.IdEq("id", event.info.id))
                }.forEach {
                    it.save(event.info.id, event.info)
                }
            }

            is com.storyteller_f.a.app.compose_app.model.OnUserUpdated -> {
                database.getCollection("users", UserInfo::class).save(event.info.id, event.info)
                database.getCollectionByPrefix("users_", UserInfo::class).filter {
                    it.exists(StorageExpression.IdEq("id", event.info.id))
                }.forEach {
                    it.save(event.info.id, event.info)
                }
            }

            is com.storyteller_f.a.app.compose_app.model.OnMediaUploaded -> {
                event.mediaInfos.forEach {
                    database.getCollection("medias_${it.owner}", MediaInfo::class).save(it.id, it)
                }
            }
        }
    }
}

private fun processTopicCreated(
    event: com.storyteller_f.a.app.compose_app.model.OnTopicCreated,
    database: StorageSource,
) {
    val topicInfo = event.topicInfo
    database.getCollection("topics_${topicInfo.parentId}", TopicInfo::class).save(topicInfo.id, topicInfo)
    with(database.getCollection("topics", TopicInfo::class)) {
        save(topicInfo.id, topicInfo)
        topicInfo.aid?.let { saveDocument(it, topicInfo) }
    }
}

private fun processTopicChanged(
    event: com.storyteller_f.a.app.compose_app.model.OnTopicChanged,
    database: StorageSource,
) {
    val topicInfo = event.topicInfo
    database.getCollection("topics_${topicInfo.parentId}", TopicInfo::class).save(topicInfo.id, topicInfo)
    with(database.getCollection("topics", TopicInfo::class)) {
        save(topicInfo.id, topicInfo)
        topicInfo.aid?.let { saveDocument(it, topicInfo) }
    }
    // 尝试更新到推荐
    with(database.getCollection("topics_0", TopicInfo::class)) {
        if (exists(StorageExpression.IdEq("id", topicInfo.id))) {
            save(topicInfo.id, topicInfo)
        }
    }
}

private fun processRemoveReaction(
    database: StorageSource,
    event: com.storyteller_f.a.app.compose_app.model.OnRemoveReaction,
) {
    database.getCollection("topic", TopicInfo::class).update(event.info.objectId) { old ->
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
        old.copy(extension = extension.copy(reactions = new), reactionCount = old.reactionCount - 1)
    }
}

private fun processOnAddReaction(
    database: StorageSource,
    event: com.storyteller_f.a.app.compose_app.model.OnAddReaction,
) {
    listOf("topics", "topics_0", "topics_${event.info.objectId}").forEach {
        database.getCollection(it, TopicInfo::class).update(event.info.objectId) { old ->
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
            old.copy(extension = extension.copy(reactions = new), reactionCount = old.reactionCount + 1)
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
