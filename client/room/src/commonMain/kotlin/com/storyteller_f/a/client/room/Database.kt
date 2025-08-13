package com.storyteller_f.a.client.room

import androidx.paging.PagingSource
import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [CommonEntity::class, CommunityEntity::class, TopicEntity::class, ReactionEntity::class],
    version = 1
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun getCommonDao(): CommonDao
    abstract fun getCommunityDao(): CommunityDao
    abstract fun getTopicDao(): TopicDao
    abstract fun getReactionDao(): ReactionDao
}

// The Room compiler generates the `actual` implementations.
@Suppress(
    "NO_ACTUAL_FOR_EXPECT",
    "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING",
    "KotlinNoActualForExpect"
)
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

expect fun getRoomDatabase(scope: String): AppDatabase

@Dao
interface CommonDao {
    @Insert(onConflict = REPLACE)
    suspend fun insert(item: CommonEntity)

    @Query("delete from CommonEntity where collection = :collection and id = :id")
    suspend fun delete(collection: String, id: String)

    @Query("select * from CommonEntity where collection = :collection and id = :id")
    suspend fun get(collection: String, id: String): CommonEntity?

    @Query("SELECT * FROM CommonEntity where collection = :collection and id = :id")
    fun getAsFlow(collection: String, id: String): Flow<CommonEntity?>

    @Query("select * from CommonEntity where collection = :collection order by id desc")
    fun getAsSource(collection: String): PagingSource<Int, CommonEntity>

    @Query("select * from CommonEntity where collection = :collection order by id desc limit :limit offset :offset")
    suspend fun getAsList(collection: String, offset: Int, limit: Int): List<CommonEntity>
}

@Entity(primaryKeys = ["collection", "id"])
data class CommonEntity(
    val id: String,
    val collection: String,
    val data: String?,
) {
    constructor(
        id: Long,
        collection: String,
        data: String
    ) : this(id.toString(), collection, data)
}

@Dao
interface CommunityDao {
    @Insert(onConflict = REPLACE)
    suspend fun insert(item: CommunityEntity)

    @Query("select * from CommunityEntity where collection = :collection order by hasPoster desc, id desc")
    fun getAsSource(collection: String): PagingSource<Int, CommunityEntity>

    @Query("select * from CommunityEntity where collection = :collection and id = :id")
    fun getAsFlow(collection: String, id: String): Flow<CommunityEntity?>

    @Query("select * from CommunityEntity where collection = :collection and id = :id")
    suspend fun get(collection: String, id: String): CommunityEntity?

    @Query("select * from CommunityEntity where collection = :collection order by id desc limit :limit offset :offset")
    suspend fun getAsList(collection: String, offset: Int, limit: Int): List<CommunityEntity>
}

@Entity(primaryKeys = ["collection", "id"], indices = [Index("collection", "hasPoster", "id")])
data class CommunityEntity(
    val id: String,
    val collection: String,
    val data: String,
    val hasPoster: Boolean
) {
    constructor(id: Long, collection: String, data: String, hasPoster: Boolean) : this(
        id.toString(),
        collection,
        data,
        hasPoster
    )
}

@Dao
interface TopicDao {
    @Insert(onConflict = REPLACE)
    suspend fun insert(item: TopicEntity)

    @Query("select * from TopicEntity where collection = :collection order by isPinned desc, id desc")
    fun getAsSource(collection: String): PagingSource<Int, TopicEntity>

    @Query("select * from TopicEntity where collection = :collection and id = :id")
    fun getAsFlow(collection: String, id: String): Flow<TopicEntity?>

    @Query("select * from TopicEntity where collection = :collection and id = :id")
    suspend fun get(collection: String, id: String): TopicEntity?

    @Query(
        "select * from TopicEntity where collection = :collection " +
            "order by isPinned desc, id desc limit :limit offset :offset"
    )
    suspend fun getAsList(collection: String, offset: Int, limit: Int): List<TopicEntity>
}

@Entity(primaryKeys = ["collection", "id"], indices = [Index("collection", "isPinned", "id")])
data class TopicEntity(
    val id: String,
    val collection: String,
    val data: String,
    val isPinned: Boolean
) {
    constructor(
        id: Long,
        collection: String,
        data: String,
        isPinned: Boolean
    ) : this(id.toString(), collection, data, isPinned)
}

@Dao
interface ReactionDao {

    @Insert(onConflict = REPLACE)
    suspend fun insert(item: ReactionEntity)

    @Query("select * from ReactionEntity where collection = :collection order by count desc, lastReactionId asc")
    fun getAsSource(collection: String): PagingSource<Int, ReactionEntity>

    @Query("select * from ReactionEntity where collection = :collection order by id desc limit :limit offset :offset")
    suspend fun getAsList(collection: String, offset: Int, limit: Int): List<ReactionEntity>
}

@Entity(
    primaryKeys = ["collection", "id"],
    indices = [Index("collection", "count", "lastReactionId")]
)
data class ReactionEntity(
    val id: String,
    val collection: String,
    val data: String,
    val count: Long,
    val lastReactionId: Long
)
