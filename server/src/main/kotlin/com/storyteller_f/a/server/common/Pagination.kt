package com.storyteller_f.a.server.common

import com.storyteller_f.BaseTable
import com.storyteller_f.shared.obj.Pagination
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.PrimaryKey
import io.ktor.server.routing.*
import io.ktor.util.converters.*
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere

inline fun <T, reified PageTokenType : Any> RoutingContext.pagination(
    nextKeyBuilder: (T) -> String,
    block: (PageTokenType?, PageTokenType?, Int) -> Result<Pair<List<T>, Long>?>
): Result<ServerResponse<T>?> {
    val v = kotlin.runCatching {
        val size = call.queryParameters.getOrFailCompact<Int>("size")

        require(size > 0) {
            "Invalid query size"
        }
        val nextPageToken = call.queryParameters["nextPageToken"]
        val prePageToken = call.queryParameters["prePageToken"]

        require(nextPageToken.isNullOrBlank() || prePageToken.isNullOrBlank()) {
            "Invalid query"
        }
        val (parsedPrePageToken, parsedNextPageToken) = if (!nextPageToken.isNullOrBlank()) {
            null to if (PageTokenType::class == ULong::class) {
                nextPageToken.toULong() as PageTokenType
            } else {
                DefaultConversionService.fromValue(nextPageToken, PageTokenType::class) as PageTokenType
            }
        } else if (!prePageToken.isNullOrBlank()) {
            if (PageTokenType::class == ULong::class) {
                prePageToken.toULong() as PageTokenType
            } else {
                DefaultConversionService.fromValue(prePageToken, PageTokenType::class) as PageTokenType
            } to null
        } else {
            null to null
        }
        Triple(parsedPrePageToken, parsedNextPageToken, size)
    }
    return when {
        v.isSuccess -> {
            val (prePageToken, nextPageToken, size) = v.getOrThrow()
            block(prePageToken, nextPageToken, size).map {
                it?.let { (list, count) ->
                    val next = if (size == list.size) {
                        nextKeyBuilder(list.last())
                    } else {
                        null
                    }
                    val pre = if (list.isNotEmpty()) {
                        nextKeyBuilder(list.first())
                    } else {
                        null
                    }
                    ServerResponse(list, Pagination(next, pre, count))
                }
            }
        }

        else -> Result.failure(v.exceptionOrNull()!!)
    }
}

fun Query.bindPaginationQuery(table: BaseTable, prePageToken: PrimaryKey?, nextPageToken: PrimaryKey?, size: Int): Query {
    if (nextPageToken != null) {
        andWhere {
            table.id less nextPageToken
        }
    } else if (prePageToken != null) {
        andWhere {
            table.id greater prePageToken
        }
    }
    return orderBy(table.id, SortOrder.DESC).limit(size)
}
