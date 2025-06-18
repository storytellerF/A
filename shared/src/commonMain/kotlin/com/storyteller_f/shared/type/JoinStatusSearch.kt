package com.storyteller_f.shared.type

enum class JoinStatusSearch {
    JOINED, NOT_JOINED, UNSPECIFIED
}

enum class PosterSearch {
    HAS_POSTER, NO_POSTER, UNSPECIFIED
}

sealed interface TopicSearch {
    data class FillComment(val id: PrimaryKey) : TopicSearch
    object Unspecified : TopicSearch
}

sealed interface JoinSearch {
    data class Joined(val uid: PrimaryKey) : JoinSearch
    data class NotJoined(val uid: PrimaryKey) : JoinSearch
    data class Unspecified(val uid: PrimaryKey?) : JoinSearch
}
