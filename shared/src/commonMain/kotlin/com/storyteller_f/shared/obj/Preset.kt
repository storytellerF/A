package com.storyteller_f.shared.obj

import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class PresetCommunity(
    val icon: String? = null,
    val name: String,
    val id: String,
    val admin: String? = null,
    val users: List<String>? = null,
    val tag: List<String>? = null,
    val font: String? = null,
)

@Serializable
data class PresetUser(
    val icon: String? = null,
    val name: String,
    val aid: String,
    val tagline: String,
    val privateKey: String,
    val id: PrimaryKey? = null,
)

@Serializable
data class PresetTopic(
    val content: String,
    val community: String? = null,
    val room: String? = null,
    val author: String,
    val type: String? = null,
    // 用于指定直接父项的偏移，用于话题的评论
    val parent: Int? = null,
    // 指定父项时需要根据level 的顺序插入
    val level: Int? = null,
    val aid: String? = null,
)

@Serializable
data class PresetRoom(
    val name: String,
    val community: String? = null,
    val users: List<String>,
    val icon: String? = null,
    val id: String,
    val admin: String
)

@Serializable
data class PresetFile(
    val owner: String,
    val paths: List<String>,
)

@Serializable
data class PresetTitle(
    val creator: String,
    val uid: String,
    val name: String,
    val scope: String,
    val scopeType: ObjectType,
    val type: TitleType,
    val description: String,
)

@Serializable
data class PresetValue(
    val type: String,
    val communityData: List<PresetCommunity>? = null,
    val userData: List<PresetUser>? = null,
    val topicData: List<PresetTopic>? = null,
    val roomData: List<PresetRoom>? = null,
    val fileData: List<PresetFile>? = null,
    val titleData: List<PresetTitle>? = null
)
