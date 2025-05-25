package com.storyteller_f.shared.type

import com.storyteller_f.shared.type.JoinStatusSearch.JOINED
import com.storyteller_f.shared.type.JoinStatusSearch.NOT_JOINED

enum class JoinStatusSearch {
    JOINED, NOT_JOINED, UNSPECIFIED
}

enum class PosterSearch {
    HAS_POSTER, NO_POSTER, UNSPECIFIED
}

sealed interface JoinSearch {
    data class Joined(val uid: PrimaryKey) : JoinSearch
    data class NotJoined(val uid: PrimaryKey) : JoinSearch
    data class Unspecified(val uid: PrimaryKey?) : JoinSearch
}

class UnauthorizedException : Exception()

fun JoinStatusSearch?.toJoinSearch(uid: PrimaryKey?): JoinSearch {
    when (this) {
        JOINED -> {
            if (uid == null) throw UnauthorizedException()
            return JoinSearch.Joined(uid)
        }
        NOT_JOINED -> {
            if (uid == null) throw UnauthorizedException()
            return JoinSearch.NotJoined(uid)
        }
        else -> return JoinSearch.Unspecified(uid)
    }
}

