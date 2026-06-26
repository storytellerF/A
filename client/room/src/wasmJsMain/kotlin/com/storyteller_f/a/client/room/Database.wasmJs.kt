package com.storyteller_f.a.client.room

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import io.github.aakira.napier.Napier
import org.w3c.dom.Worker

actual fun getRoomDatabase(scope: String): AppDatabase {
    Napier.i { "wasmJs room database: $scope.db" }
    return getDatabaseBuilder(scope)
        .fallbackToDestructiveMigrationOnDowngrade(true)
        .fallbackToDestructiveMigration(true)
        .fallbackToDestructiveMigrationFrom(true)
        .setDriver(WebWorkerSQLiteDriver(createWorker()))
        .build()
}

fun getDatabaseBuilder(scope: String): RoomDatabase.Builder<AppDatabase> {
    return Room.databaseBuilder<AppDatabase>(name = "$scope.db")
}

// The worker that implements the WebWorkerSQLiteDriver protocol is provided as an
// NPM package and resolved relative to the bundled module. Actually wiring the worker
// (npm dependency + OPFS) is out of scope for this compile check.
private fun createWorker(): Worker =
    js("""new Worker(new URL("sqlite-web-worker/worker.js", import.meta.url))""")
