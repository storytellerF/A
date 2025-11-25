package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class FileRefInfo(
    override val id: PrimaryKey,
    val objectId: PrimaryKey,
    val objectType: ObjectType,
    val author: PrimaryKey,
    val mediaName: String,
    val fileId: PrimaryKey
) : PrimaryKeyIdentifiable
