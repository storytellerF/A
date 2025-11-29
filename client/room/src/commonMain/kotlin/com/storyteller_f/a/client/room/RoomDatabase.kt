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

@Database(entities = [CommonEntity::class, TopicEntity::class, UploadEntity::class, DownloadEntity::class], version = 2)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun getCommonDao(): CommonDao
    abstract fun getTopicDao(): TopicDao
    abstract fun getUploadDao(): UploadDao
    abstract fun getDownloadDao(): DownloadDao
}

// The Room compiler generates the `actual` implementations.
@Suppress(
    "NO_ACTUAL_FOR_EXPECT",
    "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING",
    "KotlinNoActualForExpect",
    "RedundantSuppression"
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

    @Query("select * from CommonEntity where collection = :collection order by seq asc")
    fun getAsSource(collection: String): PagingSource<Int, CommonEntity>

    @Query("delete from CommonEntity where collection = :collection")
    suspend fun clean(collection: String)

    @Query("select COALESCE(MAX(seq), -1) from CommonEntity where collection = :collection")
    suspend fun getMaxSeq(collection: String): Long

    @Query("select COALESCE(MIN(seq), 1) from CommonEntity where collection = :collection")
    suspend fun getMinSeq(collection: String): Long

    suspend fun saveLast(item: CommonEntity) {
        val maxSeq = getMaxSeq(item.collection)
        insert(item.copy(seq = maxSeq + 1))
    }

    suspend fun saveFirst(item: CommonEntity) {
        val minSeq = getMinSeq(item.collection)
        insert(item.copy(seq = minSeq - 1))
    }
}

@Entity(primaryKeys = ["collection", "id"], indices = [Index("collection", "seq", unique = true)])
data class CommonEntity(
    val id: String,
    val collection: String,
    val data: String,
    val seq: Long = 0,
) {
    constructor(id: Long, collection: String, data: String, seq: Long = 0) : this(id.toString(), collection, data, seq)
}

@Dao
interface TopicDao {
    @Insert(onConflict = REPLACE)
    suspend fun insert(item: TopicEntity)

    @Query("select * from TopicEntity where collection = :collection order by isPinned desc, seq asc")
    fun getAsSource(collection: String): PagingSource<Int, TopicEntity>

    @Query("select * from TopicEntity where collection = :collection and id = :id")
    fun getAsFlow(collection: String, id: String): Flow<TopicEntity?>

    @Query("select * from TopicEntity where collection = :collection and id = :id")
    suspend fun get(collection: String, id: String): TopicEntity?

    @Query("delete from TopicEntity where collection = :collection")
    suspend fun clean(collection: String)

    @Query("select COALESCE(MAX(seq), -1) from TopicEntity where collection = :collection")
    suspend fun getMaxSeq(collection: String): Long

    @Query("select COALESCE(MIN(seq), 1) from TopicEntity where collection = :collection")
    suspend fun getMinSeq(collection: String): Long

    suspend fun saveLast(item: TopicEntity) {
        val maxSeq = getMaxSeq(item.collection)
        insert(item.copy(seq = maxSeq + 1))
    }

    suspend fun saveFirst(item: TopicEntity) {
        val minSeq = getMinSeq(item.collection)
        insert(item.copy(seq = minSeq - 1))
    }
}

@Entity(primaryKeys = ["collection", "id"], indices = [Index("collection", "isPinned", "seq", unique = true)])
data class TopicEntity(
    val id: String,
    val collection: String,
    val data: String,
    val isPinned: Boolean,
    val seq: Long = 0,
) {
    constructor(
        id: Long,
        collection: String,
        data: String,
        isPinned: Boolean,
        seq: Long = 0
    ) : this(id.toString(), collection, data, isPinned, seq)
}

@Dao
interface UploadDao {

    @Insert(onConflict = REPLACE)
    suspend fun insert(item: UploadEntity)

    @Query("select * from UploadEntity where collection = :collection and pathHash = :pathHash")
    suspend fun get(collection: String, pathHash: String): UploadEntity?

    @Query("select * from UploadEntity where collection = :collection order by id desc")
    fun getAsSource(collection: String): PagingSource<Int, UploadEntity>

    @Query("select * from UploadEntity where collection = :collection and pathHash = :pathHash")
    fun getAsFlow(collection: String, pathHash: String): Flow<UploadEntity?>

    @Query("delete from UploadEntity where collection = :collection and pathHash = :pathHash")
    suspend fun delete(collection: String, pathHash: String)

    @Query("delete from UploadEntity where collection = :collection")
    suspend fun clean(collection: String)
}

@Entity(primaryKeys = ["collection", "id"], indices = [Index("collection", "pathHash", unique = true)])
data class UploadEntity(
    val id: String,
    val collection: String,
    val data: String,
    val pathHash: String,
)

@Dao
interface DownloadDao {
    @Insert(onConflict = REPLACE)
    suspend fun insert(item: DownloadEntity)

    @Query("select * from DownloadEntity where collection = :collection and id = :id")
    suspend fun getById(collection: String, id: String): DownloadEntity?

    @Query("select * from DownloadEntity where collection = :collection and fileId = :fileId")
    suspend fun getByFileId(collection: String, fileId: Long): DownloadEntity?

    @Query("SELECT * FROM DownloadEntity where collection = :collection and id = :id")
    fun getByIdAsFlow(collection: String, id: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM DownloadEntity where collection = :collection and fileId = :fileId")
    fun getByFileIdAsFlow(collection: String, fileId: Long): Flow<DownloadEntity?>

    @Query("select * from DownloadEntity where collection = :collection order by id desc")
    fun getAsSource(collection: String): PagingSource<Int, DownloadEntity>

    @Query("delete from DownloadEntity where collection = :collection")
    suspend fun clean(collection: String)
}

@Entity(primaryKeys = ["collection", "id"], indices = [Index("collection", "fileId", unique = true)])
data class DownloadEntity(
    val id: String,
    val collection: String,
    val fileId: Long,
    val data: String,
)
