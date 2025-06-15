package com.storyteller_f.a.exposed.query

import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.backend.service.BaseTable
import com.storyteller_f.shared.type.PrimaryKey
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere

data class PaginationResult<T>(val list: List<T>, val total: Long)

fun Query.bindPaginationQuery(
    table: BaseTable,
    primaryKeyFetch: PrimaryKeyFetch
): Query {
    val cursor = primaryKeyFetch.cursor
    val order = when (cursor) {
        is Cursor.NextCursor<PrimaryKey> -> {
            andWhere {
                table.id less cursor.value
            }
            SortOrder.DESC
        }

        is Cursor.PreCursor<PrimaryKey> -> {
            andWhere {
                table.id greater cursor.value
            }
            SortOrder.ASC
        }

        null -> null
    }
    return orderBy(table.id to (order ?: SortOrder.DESC)).limit(primaryKeyFetch.size)
}
