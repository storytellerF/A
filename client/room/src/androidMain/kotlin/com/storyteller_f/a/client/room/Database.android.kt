package com.storyteller_f.a.client.room

import android.content.Context
import androidx.room.ExperimentalRoomApi
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.storyteller_f.shared.getAppContextRefValue
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalRoomApi::class)
actual fun getRoomDatabase(scope: String): AppDatabase {
    val ctx = getAppContextRefValue()!!
    val builder = getDatabaseBuilder(ctx, scope)
    return builder
        .fallbackToDestructiveMigrationOnDowngrade(true)
        .fallbackToDestructiveMigration(true)
        .setDriver(BundledSQLiteDriver())
//        .setAutoCloseTimeout(1, TimeUnit.HOURS)
//        .setDriver(AndroidSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}

fun getDatabaseBuilder(ctx: Context, scope: String): RoomDatabase.Builder<AppDatabase> {
    val appContext = ctx.applicationContext
    val dbFile = appContext.getDatabasePath("$scope.db")
    return Room.databaseBuilder<AppDatabase>(
        context = appContext,
        name = dbFile.absolutePath
    )
}
