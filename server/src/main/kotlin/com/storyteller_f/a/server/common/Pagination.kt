package com.storyteller_f.a.server.common

import com.storyteller_f.shared.model.Identifiable
import com.storyteller_f.shared.obj.Pagination
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapCatchingNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.types.Fetch
import com.storyteller_f.types.PaginationResult
import com.storyteller_f.types.PagingFetch
import io.ktor.server.routing.*
import io.ktor.util.converters.*
import kotlin.reflect.KClass

interface PagingGenerator<in T, F : Any> {
    fun parse(prePageToken: String?, nextPageToken: String?, size: Int): F

    fun generate(list: List<T>, size: Int): Pair<String?, String?>
}

abstract class CustomPagingGenerator<T>(val block: (T) -> String) : PagingGenerator<T, PagingFetch> {
    override fun parse(prePageToken: String?, nextPageToken: String?, size: Int): PagingFetch {
        val (parsedPrePageToken, parsedNextPageToken) = when {
            !nextPageToken.isNullOrBlank() -> null to getPageToken(PrimaryKey::class, nextPageToken)
            !prePageToken.isNullOrBlank() -> getPageToken(PrimaryKey::class, prePageToken) to null
            else -> null to null
        }
        return PagingFetch(parsedPrePageToken, parsedNextPageToken, size)
    }

    override fun generate(list: List<T>, size: Int): Pair<String?, String?> {
        val next = if (size <= list.size) {
            block(list.last())
        } else {
            null
        }
        val pre = if (list.isNotEmpty()) {
            block(list.first())
        } else {
            null
        }
        return pre to next
    }
}

object IdentityPagingGenerator : CustomPagingGenerator<Identifiable>({
    it.id.toString()
})

suspend fun <T, F : Fetch> RoutingContext.pagination(
    generator: PagingGenerator<T, F>,
    block: suspend (F) -> Result<PaginationResult<T>?>
): Result<ServerResponse<T>?> {
    val v = runCatching {
        val size = call.queryParameters.getOrFailCompact<Int>("size")
        require(size > 0) {
            "Invalid query size"
        }
        val nextPageToken = call.queryParameters["nextPageToken"]
        val prePageToken = call.queryParameters["prePageToken"]

        require(nextPageToken.isNullOrBlank() || prePageToken.isNullOrBlank()) {
            "Invalid query"
        }
        val fetch = generator.parse(prePageToken, nextPageToken, size)
        fetch
    }
    return v.mapResult { f ->
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
