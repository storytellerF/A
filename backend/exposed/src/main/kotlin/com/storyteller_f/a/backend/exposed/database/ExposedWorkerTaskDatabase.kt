/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.backend.exposed.database

import com.storyteller_f.a.backend.core.WorkerTaskDatabase
import com.storyteller_f.a.backend.exposed.ExposedDatabaseSession
import com.storyteller_f.a.backend.exposed.first
import com.storyteller_f.a.backend.exposed.map
import com.storyteller_f.a.backend.exposed.tables.WorkerTasks
import com.storyteller_f.a.backend.exposed.tables.wrapRow
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.model.WorkerTask
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.upsert

/** Exposed-backed implementation of [WorkerTaskDatabase]. */
internal class ExposedWorkerTaskDatabase(
    /** Database session used for executing queries. */
    val databaseSession: ExposedDatabaseSession,
) : WorkerTaskDatabase {
    override suspend fun getWorkerTask(type: TaskRecordType): Result<WorkerTask?> =
        databaseSession.dbSearch {
        search {
            WorkerTasks.selectAll().where { WorkerTasks.type eq type }
        }
        first(WorkerTask::wrapRow)
    }

    override suspend fun getWorkerTasks(): Result<List<WorkerTask>> =
        databaseSession.dbSearch {
        search {
            WorkerTasks.selectAll().orderBy(WorkerTasks.type, SortOrder.ASC)
        }
        map(WorkerTask::wrapRow)
    }

    override suspend fun upsertWorkerTasks(configs: List<WorkerTask>): Result<Unit> =
        databaseSession.dbQuery {
        configs.forEach { config ->
            WorkerTasks.upsert(WorkerTasks.type) { statement ->
                statement[WorkerTasks.type] = config.type
                statement[WorkerTasks.enabled] = config.isEnabled
                statement[WorkerTasks.fetchSize] = config.fetchSize
                statement[WorkerTasks.waitDurationMillis] = config.waitDurationMillis
            }
        }
    }
}
