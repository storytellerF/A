package com.storyteller_f.a.server.common

import com.storyteller_f.BaseTable
import com.storyteller_f.shared.obj.Pagination
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapCatchingNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.types.PaginationResult
import io.ktor.server.routing.*
import io.ktor.util.converters.*
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import kotlin.reflect.KClass

suspend fun <T, R : Any> RoutingContext.pagination(
    pageTokenType: KClass<R>,
    nextKeyBuilder: (T) -> String,
    block: suspend (R?, R?, Int) -> Result<PaginationResult<T>?>
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
        val (parsedPrePageToken, parsedNextPageToken) = when {
            !nextPageToken.isNullOrBlank() -> null to getPageToken(pageTokenType, nextPageToken)
            !prePageToken.isNullOrBlank() -> getPageToken<R>(pageTokenType, prePageToken) to null
            else -> null to null
        }
        Triple(parsedPrePageToken, parsedNextPageToken, size)
    }
    return v.mapResult { (prePageToken, nextPageToken, size) ->
        block(prePageToken, nextPageToken, size).mapCatchingNotNull { (list, count) ->
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

@Suppress("UNCHECKED_CAST")
fun <R : Any> getPageToken(pageTokenType: KClass<R>, pageToken: String): R? = if (pageTokenType == ULong::class) {
    pageToken.toULongOrNull() as? R
} else {
    DefaultConversionService.fromValue(pageToken, pageTokenType) as? R
}

fun Query.bindPaginationQuery(
    table: BaseTable,
    prePageToken: PrimaryKey?,
    nextPageToken: PrimaryKey?,
    size: Int
): Query {
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
