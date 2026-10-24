/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.result

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

internal class CustomResultTest {
    private sealed interface SourceError : FailureDetails {
        data class Rejected(override val cause: Throwable) : SourceError
    }

    private sealed interface TargetError : FailureDetails {
        data class Dependency(val source: SourceError) : TargetError {
            override val cause: Throwable get() = source.cause
        }
    }

    @Test
    fun `null remains a successful value`() {
        val result: CustomResult<String?, SourceError> = CustomResult.Success(null)
        assertTrue(result.isSuccess)
        assertFalse(result.isFailure)
        assertNull(assertIs<CustomResult.Success<String?>>(result).value)
    }

    @Test
    fun `failed mapping preserves error and skips work`() {
        val error = SourceError.Rejected(IllegalStateException("rejected"))
        val result: CustomResult<Int, SourceError> = CustomResult.Failure(error)
        val mapped = result.map { error("must not execute") }
        assertSame(error, assertIs<CustomResult.Failure<SourceError>>(mapped).error)
    }

    @Test
    fun `cross domain mapping preserves cause`() {
        val source = SourceError.Rejected(IllegalStateException("rejected"))
        val result: CustomResult<Int, SourceError> = CustomResult.Failure(source)
        val mapped: CustomResult<Int, TargetError> = result.mapError { TargetError.Dependency(it) }
        val failure = assertIs<CustomResult.Failure<TargetError>>(mapped)
        assertSame(source, assertIs<TargetError.Dependency>(failure.error).source)
        assertSame(source.cause, failure.error.cause)
    }

    @Test
    fun `failed composition maps error and skips work`() {
        val source = SourceError.Rejected(IllegalStateException("rejected"))
        val result: CustomResult<Int, SourceError> = CustomResult.Failure(source)
        val mapped =
            result.andThen<Int, SourceError, String, TargetError>(
                { TargetError.Dependency(it as SourceError) },
            ) { error("must not execute") }
        assertSame(source, assertIs<TargetError.Dependency>(mapped.errorOrNull()).source)
    }

    @Test
    fun `composition propagates callback exceptions`() {
        val failure = IllegalStateException("programming failure")
        val result: CustomResult<Int, SourceError> = CustomResult.Success(1)
        assertSame(
            failure,
            assertFailsWith<IllegalStateException> {
                result.flatMap<Int, SourceError, Int> { throw failure }
            },
        )
    }

    @Test
    fun `capture classifies ordinary exceptions`() {
        val failure = IllegalArgumentException("invalid")
        val result = captureFailure(SourceError::Rejected) { throw failure }
        assertSame(failure, assertIs<SourceError.Rejected>(result.errorOrNull()).cause)
    }

    @Test
    fun `capture propagates cancellation before mapping`() {
        val cancellation = CancellationException("cancelled")
        assertSame(
            cancellation,
            assertFailsWith<CancellationException> {
                captureFailure({ _: Throwable -> error("must not classify cancellation") }) { throw cancellation }
            },
        )
    }

    @Test
    fun `capture propagates fatal errors`() {
        val fatal = object : Error("fatal") { }
        assertSame(
            fatal,
            assertFailsWith<Error> {
                captureFailure(SourceError::Rejected) { throw fatal }
            },
        )
    }

    @Test
    fun `nullable composition skips absent values`() {
        val result: CustomResult<Int?, SourceError> = CustomResult.Success(null)
        val mapped =
            result.andThenPresent<Int, SourceError, String, TargetError>(
                { TargetError.Dependency(it as SourceError) },
            ) { error("must not execute") }
        assertTrue(mapped.isSuccess)
        assertNull(mapped.getOrNull())
    }

    @Test
    fun `fold exposes the scenario error`() {
        val cause = IllegalArgumentException("invalid")
        val source = SourceError.Rejected(cause)
        val result: CustomResult<Int, SourceError> = CustomResult.Failure(source)
        assertSame(source, result.fold({ error("must not execute") }, { it }))
    }
}
