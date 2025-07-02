package com.storyteller_f.a.backend.exposed.query

import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.ReactionFetch
import com.storyteller_f.a.backend.exposed.tables.Reactions
import com.storyteller_f.shared.obj.ReactionCursorKey
import com.storyteller_f.shared.type.PrimaryKey
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll

fun buildReactionInfoQuery(objectId: List<PrimaryKey>, reactionFetch: ReactionFetch): Query {
    val query = Reactions.selectAll().where {
        Reactions.objectId inList objectId
    }
    val cursor = reactionFetch.cursor
    when (cursor) {
        is Cursor.NextCursor<ReactionCursorKey> -> query.andWhere {
            val value = cursor.value
            Reactions.count greaterEq value.count and (Reactions.lastReactionId less value.reactionId)
        }

        is Cursor.PreCursor<ReactionCursorKey> -> query.andWhere {
            val value = cursor.value
            Reactions.count lessEq value.count and (Reactions.lastReactionId greater value.reactionId)
        }

        null -> {}
    }
    return query
}
