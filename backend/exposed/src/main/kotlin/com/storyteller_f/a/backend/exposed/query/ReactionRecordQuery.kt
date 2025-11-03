package com.storyteller_f.a.backend.exposed.query

import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.ReactionFetch
import com.storyteller_f.a.backend.exposed.tables.Reactions
import com.storyteller_f.shared.obj.ReactionCursorKey
import com.storyteller_f.shared.type.PrimaryKey
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll

fun buildReactionInfoQuery(objectId: List<PrimaryKey>, reactionFetch: ReactionFetch): Query {
    val query = Reactions.selectAll().where {
        Reactions.objectId inList objectId
    }
    val cursor = reactionFetch.cursor
    when (cursor) {
        is Cursor.DescCursor<ReactionCursorKey> -> query.andWhere {
            val value = cursor.value
            Reactions.count greaterEq value.count and (Reactions.lastReactionId less value.reactionId)
        }

        is Cursor.AscCursor<ReactionCursorKey> -> query.andWhere {
            val value = cursor.value
            Reactions.count lessEq value.count and (Reactions.lastReactionId greater value.reactionId)
        }

        null -> {}
    }
    return query
}
