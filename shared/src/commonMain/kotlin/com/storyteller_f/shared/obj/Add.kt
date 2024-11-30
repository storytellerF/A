package com.storyteller_f.shared.obj

import kotlinx.serialization.Serializable

@Serializable
data class PresetCommunity(
    val icon: String? = null,
    val name: String,
    val id: String,
    val users: List<String>? = null,
    val tag: List<String>? = null,
)

@Serializable
data class PresetUser(
    val icon: String? = null,
    val name: String,
    val id: String,
    val tagline: String,
    val privateKey: String,
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
data class PresetValue(
    val type: String,
    val communityData: List<PresetCommunity>? = null,
    val userData: List<PresetUser>? = null,
    val topicData: List<PresetTopic>? = null,
    val roomData: List<PresetRoom>? = null
)
