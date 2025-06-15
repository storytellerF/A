package com.storyteller_f.a.server.common

import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.Fetch
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.ReactionFetch
import com.storyteller_f.a.exposed.query.PaginationResult
import com.storyteller_f.backend.service.Backend
import com.storyteller_f.shared.model.Identifiable
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.obj.Pagination
import com.storyteller_f.shared.obj.ReactionCursorKey
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapCatchingNotNull
import com.storyteller_f.shared.utils.mapResult
import io.ktor.server.routing.*
import io.ktor.util.converters.*
import kotlin.reflect.KClass

interface PagingGenerator<in T, F : Any> {
    fun parse(prePageToken: String?, nextPageToken: String?, size: Int): F

    fun generate(list: List<T>, size: Int): Pair<String?, String?>
}

abstract class PrimaryKeyPagingGenerator<T>(val block: (T) -> PrimaryKey) : PagingGenerator<T, PrimaryKeyFetch> {
    override fun parse(prePageToken: String?, nextPageToken: String?, size: Int): PrimaryKeyFetch {
        return PrimaryKeyFetch(
            when {
                !nextPageToken.isNullOrBlank() -> getPageToken(
                    PrimaryKey::class,
                    nextPageToken
                )?.let { Cursor.NextCursor(it) }

                !prePageToken.isNullOrBlank() -> getPageToken(PrimaryKey::class, prePageToken)?.let {
                    Cursor.PreCursor(
                        it
                    )
                }

                else -> null
            },
            size
        )
    }

    override fun generate(list: List<T>, size: Int): Pair<String?, String?> {
        val next = if (size <= list.size) {
            block(list.last()).toString()
        } else {
            null
        }
        val pre = if (list.isNotEmpty()) {
            block(list.first()).toString()
        } else {
            null
        }
        return pre to next
    }
}

object IdentifiablePagingGenerator : PrimaryKeyPagingGenerator<Identifiable>(Identifiable::id)

suspend fun <T, F : Fetch> RoutingContext.pagination(
    generator: PagingGenerator<T, F>,
    block: suspend (F) -> Result<PaginationResult<T>?>
): Result<ServerResponse<T>?> {
    return runCatching {
        val size = call.queryParameters.getOrFailCompact<Int>("size")
        require(size > 0) {
            "Invalid query size"
        }
        val nextPageToken = call.queryParameters["nextPageToken"]
        val prePageToken = call.queryParameters["prePageToken"]

        require(nextPageToken.isNullOrBlank() || prePageToken.isNullOrBlank()) {
            "Invalid query"
        }
        generator.parse(prePageToken, nextPageToken, size)
    }.mapResult { f ->
        block(f).mapCatchingNotNull { (list, count) ->
            val (pre, next) = generator.generate(list, f.size)
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

class ReactionPaginationGenerator(val backend: Backend) : PagingGenerator<ReactionInfo, ReactionFetch> {
    override fun parse(prePageToken: String?, nextPageToken: String?, size: Int): ReactionFetch {
        return ReactionFetch(
            when {
                !nextPageToken.isNullOrBlank() -> Cursor.NextCursor(
                    backend.json.decodeFromString<ReactionCursorKey>(
                        nextPageToken
                    )
                )

                !prePageToken.isNullOrBlank() -> Cursor.PreCursor(
                    backend.json.decodeFromString<ReactionCursorKey>(
                        prePageToken
                    )
                )

                else -> null
            },
            size
        )
    }

    override fun generate(list: List<ReactionInfo>, size: Int): Pair<String?, String?> {
        val next = if (size <= list.size) {
            val last = list.last()
            backend.json.encodeToString(ReactionCursorKey(last.count, last.lastReactionId))
        } else {
            null
        }
        val pre = if (list.isNotEmpty()) {
            val first = list.first()
            backend.json.encodeToString(ReactionCursorKey(first.count, first.lastReactionId))
        } else {
            null
        }
        return pre to next
    }
}
