package com.storyteller_f.a.backend.core

import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.obj.ReactionCursorKey
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import java.io.File

class Config(
    val databaseConnection: DatabaseConnection,
    val buildType: String,
    val flavor: String
)

data class ElasticConnection(val url: String, val certFile: String, val name: String, val pass: String)
data class MinIoConnection(val url: String, val user: String, val pass: String)
data class DatabaseConnection(val uri: String, val driver: String, val user: String, val password: String)

class UnauthorizedException : Exception()
class ForbiddenException(message: String = "Invalid operation") : Exception(message)
class CustomBadRequestException(message: String) : Exception(message)

data class UploadPack(
    val path: File,
    val name: String,
    val owner: PrimaryKey,
    val ownerType: ObjectType,
    val size: Long,
    val contentType: String = "",
    val dimension: Dimension? = null
) {
    val newFullName = "$owner/$name"
}

data class CopyPack(val origin: String, val new: String)

sealed interface ObjectFetch {
    data class AidFetch(val aid: String) : ObjectFetch
    data class IdFetch(val id: PrimaryKey) : ObjectFetch
}

sealed interface ObjectListFetch {
    data class AidListFetch(val aidList: List<String>) : ObjectListFetch
    data class IdListFetch(val idList: List<PrimaryKey>) : ObjectListFetch
}

sealed interface Cursor<T> {
    data class PreCursor<T>(val value: T) : Cursor<T>
    data class NextCursor<T>(val value: T) : Cursor<T>
}

interface Fetch {
    val size: Int
}

interface GenericFetch<T> : Fetch {
    val cursor: Cursor<T>?
}

data class PrimaryKeyFetch(override val cursor: Cursor<PrimaryKey>?, override val size: Int) :
    GenericFetch<PrimaryKey>

data class ReactionFetch(override val cursor: Cursor<ReactionCursorKey>?, override val size: Int) :
    GenericFetch<ReactionCursorKey>

sealed interface JoinSearch {
    data class Joined(val uid: PrimaryKey) : JoinSearch
    data class NotJoined(val uid: PrimaryKey) : JoinSearch
    data class Unspecified(val uid: PrimaryKey?) : JoinSearch
}
