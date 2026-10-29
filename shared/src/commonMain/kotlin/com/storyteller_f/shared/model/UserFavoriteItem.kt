/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class UserFavoriteItem(
    val favoriteInfo: UserFavoriteInfo,
    val topicInfo: TopicDetail? = null,
    val communityInfo: CommunityInfo? = null,
    val roomInfo: RoomInfo? = null,
    val userInfo: UserInfo? = null,
    val titleInfo: TitleDetail? = null,
    val fileInfo: FileInfo? = null,
) : PrimaryKeyIdentifiable {
    override val id: PrimaryKey get() = favoriteInfo.id
    val uid: PrimaryKey get() = favoriteInfo.uid
    val objectId: PrimaryKey get() = favoriteInfo.objectId
    val objectType: ObjectType get() = favoriteInfo.objectType
    val createdTime: LocalDateTime get() = favoriteInfo.createdTime
    companion object {
        val EMPTY = UserFavoriteItem(UserFavoriteInfo.EMPTY)
    }
}
