package com.storyteller_f.storage

import com.storyteller_f.shared.model.AlternativeAccountInfo
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TitleSearchType
import com.storyteller_f.shared.model.TitleStatus
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.ReactionCursorKey
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface DocumentObservable<T> {
    val deferred: CompletableDeferred<List<T>>

    fun remove()
}

sealed interface CollectionName {
    fun getName(): String

    data object Users : CollectionName {
        override fun getName(): String {
            return "users"
        }
    }

    data object Topics : CollectionName {
        override fun getName(): String {
            return "topics"
        }
    }

    data object Titles : CollectionName {
        override fun getName(): String {
            return "title"
        }
    }

    data object Rooms : CollectionName {
        override fun getName(): String {
            return "rooms"
        }
    }

    data object Communities : CollectionName {
        override fun getName(): String {
            return "communities"
        }
    }

    data class SearchUser(val word: String) : CollectionName {
        override fun getName(): String {
            return "users_$word"
        }
    }

    data class SearchRoom(val word: String, val community: PrimaryKey?) : CollectionName {
        override fun getName(): String {
            return "rooms_${word}_$community"
        }
    }

    data class SearchTopic(
        val word: List<String>,
        val parentId: PrimaryKey?,
    ) : CollectionName {
        override fun getName(): String {
            return "topics_${word}_$parentId"
        }
    }

    data class SearchCommunity(
        val joinStatusSearch: JoinStatusSearch,
        val word: String,
        val target: PrimaryKey? = null,
    ) : CollectionName {
        override fun getName(): String {
            return "communities_${word}_${target}_$joinStatusSearch"
        }
    }

    data object Recommend : CollectionName {
        override fun getName(): String {
            return "topics_recommend"
        }
    }

    data object Reactions : CollectionName {
        override fun getName(): String {
            return "reactions"
        }
    }

    data class ReactionList(val objectId: PrimaryKey) : CollectionName {
        override fun getName(): String {
            return "reactions_$objectId"
        }

        fun encodeKey(json: Json, it: ReactionInfo): String {
            return json.encodeToString(ReactionCursorKey(it.count, it.lastReactionId))
        }

        fun decodeKey(json: Json, key: String): ReactionCursorKey {
            return json.decodeFromString(key)
        }
    }

    data class SearchTitle(
        val uid: PrimaryKey,
        val searchType: TitleSearchType,
        val status: TitleStatus? = null,
        val type: TitleType? = null,
        val scopeId: PrimaryKey? = null,
    ) : CollectionName {
        override fun getName(): String {
            return "titles_${uid}_${searchType}_${status}_${type}_$scopeId"
        }
    }

    data object Alternatives : CollectionName {
        override fun getName(): String {
            return "alternatives"
        }
    }

    data class TopicList(val objectId: PrimaryKey) : CollectionName {
        override fun getName(): String {
            return "topics_$objectId"
        }
    }

    data class Medias(val objectId: PrimaryKey) : CollectionName {
        override fun getName(): String {
            return "medias_$objectId"
        }
    }

    data class Members(val word: String, val objectId: PrimaryKey) : CollectionName {
        override fun getName(): String {
            return "members_${objectId}_$word"
        }
    }

    data object Download : CollectionName {
        override fun getName(): String {
            return "downloads"
        }
    }
}

@Serializable
data class RemoteKeys(val collectionName: String, val key: String?)

interface SaveStorage<T> {
    fun save(collectionName: CollectionName, t: T)
}

interface ObservePrimaryKeyDatumStorage<T> {
    fun observeDatum(collectionName: CollectionName, id: PrimaryKey): Flow<T?>
}

interface ObserveDataStorage<T> {
    fun observeData(
        collectionName: CollectionName,
        key: String?,
        loadSize: Int,
        invalidate: () -> Unit
    ): DocumentObservable<T>
}

interface ObserveDatumStorage<T> {
    fun observeDatum(collectionName: CollectionName, key: String): Flow<T?>
}

interface GetStorage<T> {
    fun getDocument(collectionName: CollectionName, id: PrimaryKey): T?
}

interface Storage {
    val userStorage: UserStorage
    val communityStorage: CommunityStorage
    val topicStorage: TopicStorage
    val titleStorage: TitleStorage
    val roomStorage: RoomStorage
    val remoteKeyStorage: RemoteKeyStorage
    val reactionStorage: ReactionStorage
    val alternativesStorage: AlternativesStorage
    val mediasStorage: MediasStorage
    val downloadStorage: DownloadStorage

    companion object {
        val EMPTY = object : Storage {
            override val userStorage: UserStorage
                get() = TODO("Not yet implemented")
            override val communityStorage: CommunityStorage
                get() = TODO("Not yet implemented")
            override val topicStorage: TopicStorage
                get() = TODO("Not yet implemented")
            override val titleStorage: TitleStorage
                get() = TODO("Not yet implemented")
            override val roomStorage: RoomStorage
                get() = TODO("Not yet implemented")
            override val remoteKeyStorage: RemoteKeyStorage
                get() = TODO("Not yet implemented")
            override val reactionStorage: ReactionStorage
                get() = TODO("Not yet implemented")
            override val alternativesStorage: AlternativesStorage
                get() = TODO("Not yet implemented")
            override val mediasStorage: MediasStorage
                get() = TODO("Not yet implemented")
            override val downloadStorage: DownloadStorage
                get() = TODO("Not yet implemented")
        }
    }
}

interface UserStorage : SaveStorage<UserInfo>, ObserveDataStorage<UserInfo>, ObserveDatumStorage<UserInfo>,
    ObservePrimaryKeyDatumStorage<UserInfo>

interface CommunityStorage : SaveStorage<CommunityInfo>, ObserveDataStorage<CommunityInfo>,
    ObserveDatumStorage<CommunityInfo>, ObservePrimaryKeyDatumStorage<CommunityInfo>, GetStorage<CommunityInfo>

interface TopicStorage : SaveStorage<TopicInfo>, ObserveDataStorage<TopicInfo>,
    ObserveDatumStorage<TopicInfo>, ObservePrimaryKeyDatumStorage<TopicInfo>, GetStorage<TopicInfo>

interface TitleStorage : SaveStorage<TitleInfo>, ObserveDataStorage<TitleInfo>,
    ObservePrimaryKeyDatumStorage<TitleInfo>

interface RoomStorage : SaveStorage<RoomInfo>, ObserveDataStorage<RoomInfo>, ObserveDatumStorage<RoomInfo>,
    ObservePrimaryKeyDatumStorage<RoomInfo>

interface ReactionStorage : SaveStorage<ReactionInfo>, ObserveDataStorage<ReactionInfo>,
    ObservePrimaryKeyDatumStorage<ReactionInfo>

interface AlternativesStorage : SaveStorage<AlternativeAccountInfo>,
    ObserveDataStorage<AlternativeAccountInfo>

interface MediasStorage : SaveStorage<MediaInfo>, ObserveDataStorage<MediaInfo>

interface DownloadStorage : SaveStorage<DownloadInfo>, ObservePrimaryKeyDatumStorage<DownloadInfo>,
    GetStorage<DownloadInfo>

interface RemoteKeyStorage {
    fun getPreRemoteKey(collectionName: CollectionName): RemoteKeys?
    fun getNextRemoteKey(collectionName: CollectionName): RemoteKeys?
    fun savePreRemoteKey(remoteKeys: RemoteKeys)
    fun saveNextRemoteKey(remoteKeys: RemoteKeys)

    fun deletePreRemoteKey(collectionName: CollectionName)
    fun deleteNextRemoteKey(collectionName: CollectionName)
}

fun <T, Storage> Storage.update(
    collectionName: CollectionName,
    id: PrimaryKey,
    block: (T) -> T
) where Storage : SaveStorage<T>, Storage : GetStorage<T> {
    val document = getDocument(collectionName, id) ?: return
    val value = block(document)
    save(collectionName, value)
}
