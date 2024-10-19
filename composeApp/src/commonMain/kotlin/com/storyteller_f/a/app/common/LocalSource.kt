package com.storyteller_f.a.app.common

import androidx.paging.PagingState
import app.cash.paging.*
import com.storyteller_f.shared.model.Identifiable
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier
import kotbase.*
import kotbase.ktx.*
import kotlinx.coroutines.CompletableDeferred

val database by lazy {
    Database(
        "a-db",
        DatabaseConfigurationFactory.newConfig(
            okio.FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("a-client").toString()
        )
    ).apply {
        Database.log.console.domains = LogDomain.ALL_DOMAINS
        Database.log.console.level = LogLevel.INFO
    }
}

fun getOrCreateCollection(collectionName: String) =
    database.defaultScope.getCollection(collectionName) ?: database.createCollection(
        collectionName
    )

class CustomQueryPagingSource<RowType : Identifiable>(
    private val select: Select,
    collectionName: String,
    private val queryProvider: From.(PrimaryKey?) -> LimitRouter,
    private val mapMapper: ((Map<String, Any?>) -> RowType?)? = null,
    private val jsonStringMapper: ((String) -> RowType?)? = null,
) : PagingSource<PrimaryKey, RowType>() {

    private val collection = getOrCreateCollection(collectionName)

    init {
        require(mapMapper != null || jsonStringMapper != null) { "At least one mapper must be not null" }

        registerInvalidatedCallback {
            removeAllToken()
        }
    }

    override fun getRefreshKey(state: PagingState<PrimaryKey, RowType>): PrimaryKey? {
        return null
    }

    private fun ResultSet.toObjects(): List<RowType> {
        return if (mapMapper != null) {
            toObjects(mapMapper)
        } else {
            toObjects(jsonStringMapper!!)
        }
    }

    val map = mutableMapOf<PrimaryKey, ListenerToken>()

    override suspend fun load(
        params: PagingSourceLoadParams<PrimaryKey>
    ): PagingSourceLoadResult<PrimaryKey, RowType> {
        val key = params.key
        if (key == null) {
            removeAllToken()
        }
        Napier.v(tag = "pagination") {
            "source load $key"
        }
        return try {
            val task = CompletableDeferred<List<RowType>>()
            val listenerToken = select.from(collection)
                .queryProvider(key)
                .limit(params.loadSize)
                .addChangeListener {
                    val results = it.results
                    when {
                        task.isCompleted -> invalidate()
                        results == null -> task.completeExceptionally(it.error ?: Exception("it.error is null"))
                        else -> task.complete(results.toObjects())
                    }
                }
            map[key ?: (0u)] = listenerToken
            val data = task.await()
            Napier.v(tag = "pagination") {
                "source nextKey = ${data.lastOrNull()?.id} ${data.size}"
            }
            PagingSourceLoadResultPage(
                data = data,
                prevKey = null,
                nextKey = data.lastOrNull()?.id,
            )
        } catch (e: Exception) {
            Napier.e(e, tag = "pagination") {
                "source load error"
            }
            PagingSourceLoadResultError(e)
        }
    }

    private fun removeAllToken() {
        map.values.forEach {
            it.remove()
        }
        map.clear()
    }
}
