package com.storyteller_f.a.cloud.server

import com.storyteller_f.a.api.PaginationQuery
import com.storyteller_f.a.backend.core.types.TaskRecord
import com.storyteller_f.a.client.core.getTaskRecords
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.utils.now
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminTaskRecordTest {
    @Test
    fun `admin list worker task records`() {
        test {
            val outer = attachPanelSession()
            withWorkerBackend { backend ->
                listOf(
                    TaskRecord(101, now(), TaskRecordType.INTRO, 1001),
                    TaskRecord(102, now(), TaskRecordType.TITLE, 1002),
                    TaskRecord(103, now(), TaskRecordType.SUBSCRIPTION, 1003),
                    TaskRecord(
                        id = 104,
                        createdTime = now(),
                        type = TaskRecordType.TOPIC_MODERATION,
                        objectId = 1004,
                        isSuccess = false,
                        failureType = TaskRecordType.MODEL_RESPONSE_FAILURE,
                        failureReason = "unexpected model output",
                    ),
                ).forEach {
                    backend.database.admin.createTaskRecord(it).getOrThrow()
                }
            }
            loginPanelSession(outer) {
                val firstPage = getTaskRecords(null, PaginationQuery(size = 2)).getOrThrow()
                assertEquals(listOf(104L, 103L), firstPage.data.map { it.id })
                assertEquals(4, firstPage.pagination?.total)

                val secondPage =
                    getTaskRecords(
                        null,
                        PaginationQuery(nextPageToken = firstPage.pagination?.nextPageToken, size = 2),
                    ).getOrThrow()
                assertEquals(listOf(102L, 101L), secondPage.data.map { it.id })

                val filtered = getTaskRecords(TaskRecordType.TITLE, PaginationQuery()).getOrThrow()
                assertEquals(listOf(TaskRecordType.TITLE), filtered.data.map { it.type })

                val failed =
                    getTaskRecords(
                        type = TaskRecordType.TOPIC_MODERATION,
                        query = PaginationQuery(),
                        isSuccess = false,
                        failureType = TaskRecordType.MODEL_RESPONSE_FAILURE,
                    ).getOrThrow()
                assertEquals(listOf(104L), failed.data.map { it.id })

                val summaries = getTaskRecordSummaries().getOrThrow().data.associateBy { it.type }
                assertEquals(1, summaries[TaskRecordType.TOPIC_MODERATION]?.failureCount)
                assertEquals(1, summaries[TaskRecordType.TITLE]?.successCount)

                markTaskRecordForRetry(104).getOrThrow()
                val retryRequested =
                    getTaskRecords(
                        TaskRecordType.TOPIC_MODERATION,
                        PaginationQuery(),
                        isSuccess = false,
                    ).getOrThrow()
                assertEquals(true, retryRequested.data.single().isRetryRequested)
            }
        }
    }
}
