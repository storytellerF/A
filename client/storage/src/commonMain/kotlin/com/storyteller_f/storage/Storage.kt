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
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

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

interface CommonStorage<T> {
    fun save(collectionName: CollectionName, t: T)
}

interface CustomStorage3<T> {
    fun observeDatum(collectionName: CollectionName, id: PrimaryKey): Flow<T?>
}

interface CustomStorage1<T> {
    fun observeData(
        collectionName: CollectionName,
        key: String?,
        loadSize: Int,
        invalidate: () -> Unit
    ): DocumentObservable<T>
}

interface CustomStorage2<T> {
    fun observeDatum(collectionName: CollectionName, key: String): Flow<T?>
}

interface CustomStorage4<T> {
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

interface UserStorage : CommonStorage<UserInfo>, CustomStorage1<UserInfo>, CustomStorage2<UserInfo>,
    CustomStorage3<UserInfo>

interface CommunityStorage : CommonStorage<CommunityInfo>, CustomStorage1<CommunityInfo>,
    CustomStorage2<CommunityInfo>, CustomStorage3<CommunityInfo>, CustomStorage4<CommunityInfo>

interface TopicStorage : CommonStorage<TopicInfo>, CustomStorage1<TopicInfo>,
    CustomStorage2<TopicInfo>, CustomStorage3<TopicInfo>, CustomStorage4<TopicInfo>

interface TitleStorage : CommonStorage<TitleInfo>, CustomStorage1<TitleInfo>,
    CustomStorage3<TitleInfo>

interface RoomStorage : CommonStorage<RoomInfo>, CustomStorage1<RoomInfo>, CustomStorage2<RoomInfo>,
    CustomStorage3<RoomInfo>

interface ReactionStorage : CommonStorage<ReactionInfo>, CustomStorage1<ReactionInfo>,
    CustomStorage3<ReactionInfo>

interface AlternativesStorage : CommonStorage<AlternativeAccountInfo>,
    CustomStorage1<AlternativeAccountInfo>

interface MediasStorage : CommonStorage<MediaInfo>, CustomStorage1<MediaInfo>

interface DownloadStorage : CommonStorage<DownloadInfo>, CustomStorage3<DownloadInfo>,
    CustomStorage4<DownloadInfo>

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
) where Storage : CommonStorage<T>, Storage : CustomStorage4<T> {
    val document = getDocument(collectionName, id) ?: return
    val value = block(document)
    save(collectionName, value)
}
