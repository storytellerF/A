/*
 * This is a private project. All rights reserved.
 */

package com.storyteller.a.client.room

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class CancellationSafeSQLiteDriverTest {
    @Test
    internal fun cancelledRequestWaitsForWorkerResponse() = runTest { verifyCancellation() }

    private suspend fun TestScope.verifyCancellation() {
        val requestStarted = CompletableDeferred<Unit>()
        val workerResponse = CompletableDeferred<String>()
        val job =
            launch {
                awaitWorkerResponse {
                    requestStarted.complete(Unit)
                    workerResponse.await()
                }
            }

        requestStarted.await()
        job.cancel()
        testScheduler.runCurrent()
        assertFalse(job.isCompleted)

        workerResponse.complete("done")
        testScheduler.runCurrent()
        assertTrue(job.isCancelled)
    }
}
