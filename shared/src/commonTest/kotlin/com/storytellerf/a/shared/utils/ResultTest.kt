/*
 * This is a private project. All rights reserved.
 */
package com.storytellerf.a.shared.utils

import com.storyteller_f.shared.utils.mapCatchingNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.recoverResult
import com.storyteller_f.shared.utils.unit
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

internal class ResultTest {
    @Test
    fun `mapResult rethrows thrown cancellation`(): TestResult {
        val cancellation = CancellationException("cancelled")
        return runTest {
            val thrown =
                assertFailsWith<CancellationException> {
                    Result.success(Unit).mapResult<Unit, Unit> {
                        throw cancellation
                    }
                }

            assertSame(cancellation, thrown)
        }
    }

    @Test
    fun `mapResult rethrows returned cancellation`(): TestResult {
        val cancellation = CancellationException("cancelled")
        return runTest {
            val thrown =
                assertFailsWith<CancellationException> {
                    Result.success(Unit).mapResult<Unit, Unit> {
                        Result.failure(cancellation)
                    }
                }

            assertSame(cancellation, thrown)
        }
    }

    @Test
    fun `mapCatching rethrows cancellation`(): TestResult {
        val cancellation = CancellationException("cancelled")
        return runTest {
            val thrown =
                assertFailsWith<CancellationException> {
                    Result.success<Unit?>(Unit).mapCatchingNotNull<Unit, Unit> {
                        throw cancellation
                    }
                }

            assertSame(cancellation, thrown)
        }
    }

    @Test
    fun `transforms rethrow wrapped cancellation`(): TestResult {
        val cancellation = CancellationException("cancelled")
        return runTest {
            val result = Result.failure<Unit>(cancellation)

            assertSame(
                cancellation,
                assertFailsWith<CancellationException> {
                    result.recoverResult { Result.success(Unit) }
                },
            )
            assertSame(
                cancellation,
                assertFailsWith<CancellationException> {
                    result.unit()
                },
            )
        }
    }

    @Test
    fun `mapResult wraps ordinary exceptions`(): TestResult {
        val failure = IllegalStateException("failed")
        return runTest {
            val result =
                Result.success(Unit).mapResult<Unit, Unit> {
                    throw failure
                }

            assertSame(failure, result.exceptionOrNull())
        }
    }
}
