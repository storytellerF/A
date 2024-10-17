/*
 * Copyright (c) 2023 Perraco Labs. All rights reserved.
 * This work is licensed under the terms of the MIT license.
 * For a copy, see <https://opensource.org/licenses/MIT>
 */

package com.perraco.utils

import com.storyteller_f.shared.type.OKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import kotlinx.datetime.*
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Generates unique identifiers suitable for distributed systems based
 * on the current system time, machine ID, and an atomic increment value.
 *
 * These IDs are useful in scenarios where unique identification across
 * multiple machines is needed.
 *
 * See: [Snowflake ID](https://en.wikipedia.org/wiki/Snowflake_ID)
 */
object SnowflakeFactory {

    // The unique machine ID used for generating Snowflake IDs.
    // Must be set before generating IDs.
    // This value must be unique for each machine in a distributed system.
    private var machineId: Int? = null

    // Tracks the last timestamp in milliseconds when an ID was generated.
    // Initialized to -1 to indicate no IDs have been generated yet.
    private var lastTimestampMs: Long = -1L

    // Tracks the sequence number for generating multiple unique IDs within the same millisecond.
    // Initialized to 0 and incremented for each ID generated in the same millisecond. This value
    // will typically be 0 if IDs are not generated at a frequency higher than one per millisecond.
    private var sequence: Long = 0L

    // Number of bits allocated for the machine ID within the 64-bit Snowflake ID.
    // Minimum 1 bit for at least 2 unique IDs. 10 bits allows 2^10 = 1,024 IDs.
    private const val MACHINE_ID_BITS: Int = 10

    // Number of bits for the sequence number, part of the 64-bit limit.
    // Minimum 1 bit for 2 IDs per millisecond. 12 bits allows 2^12 = 4,096 IDs per millisecond.
    private const val SEQUENCE_BITS: Int = 12

    // Maximum possible value for machine ID, derived from the number of bits allocated.
    // This value is 2^MACHINE_ID_BITS - 1.
    const val MAX_MACHINE_ID = (1 shl MACHINE_ID_BITS) - 1

    // Maximum possible value for the sequence number, based on the allocated bits.
    // Equals 2^SEQUENCE_BITS - 1, ensuring a unique ID sequence within a millisecond.
    private const val MAX_SEQUENCE = (1 shl SEQUENCE_BITS) - 1

    // Wall-clock reference time set at SnowflakeFactory initialization.
    // Utilized in `newTimestamp()` to compute stable millisecond timestamps,
    // combining with elapsed time since initialization for adjustment-resilient values.
    private val timestampEpoch: Long = System.currentTimeMillis()

    // Nanosecond-precision timestamp recorded at SnowflakeFactory initialization.
    // Used alongside System.currentTimeMillis() in `newTimestamp()` to ensure
    // monotonically increasing timestamps, immune to system clock modifications.
    private val nanoTimeStart: Long = System.nanoTime()

    /**
     * Sets the unique machine ID for generating Snowflake IDs.
     * This is the first step required before generating IDs.
     * This value must be unique for each machine in a distributed system.
     *
     * @param id The machine ID to use for generating Snowflake IDs.
     * @throws IllegalArgumentException If the machine ID is outside the allowable range.
     */
    fun setMachine(id: Int) {
        require(id in 0..MAX_MACHINE_ID) { "The Machine ID must be between 0 and $MAX_MACHINE_ID" }
        machineId = id
    }

    private val lock = Mutex()

    suspend fun nextId(): OKey {
        return lock.withLock {
            nextIdInternal()
        }
    }

    /**
     * Generates the next unique Snowflake ID.
     * @return The generated Snowflake ID in the configured base alphanumeric string.
     * @throws IllegalStateException If the system clock has moved backwards, breaking the ID sequence.
     */
    private suspend fun nextIdInternal(): OKey {
        var currentTimestampMs: Long = newTimestamp()

        // Check for invalid system clock settings.
        check(currentTimestampMs >= lastTimestampMs) {
            "Invalid System Clock. Current timestamp: $currentTimestampMs, last timestamp: $lastTimestampMs"
        }

        // If it's a new millisecond, reset the sequence number.
        if (currentTimestampMs != lastTimestampMs) {
            sequence = 0L
            lastTimestampMs = currentTimestampMs
        } else {
            // If the current timestamp is the same, increment the sequence number.
            // If sequence overflows, wait for the next millisecond.
            if (++sequence > MAX_SEQUENCE) {
                sequence = 0L
                do {
                    yield()
                    currentTimestampMs = newTimestamp()
                } while (currentTimestampMs <= lastTimestampMs)
                lastTimestampMs = currentTimestampMs
            }
        }

        // Construct the ID.

        return (lastTimestampMs.toULong() shl (MACHINE_ID_BITS + SEQUENCE_BITS)) or
            (machineId!!.toULong() shl SEQUENCE_BITS) or
            sequence.toULong()
    }

    /**
     * Parses a Snowflake ID to extract its segments.
     * The ID is expected to have an optional "id-" prefix.
     * @param id The Snowflake ID to parse.
     * @return SnowflakeData containing the ID segments.
     */
    fun parse(id: OKey): SnowflakeData {
        // Extract the machine ID segment.
        val machineIdSegment = (id shr SEQUENCE_BITS) and MAX_MACHINE_ID.toULong()

        // Extract the timestamp segment.
        val timestampMs: Long = (id shr (MACHINE_ID_BITS + SEQUENCE_BITS)).toLong()
        val instant: Instant = Instant.fromEpochMilliseconds(timestampMs)
        val utcTimestampSegment: LocalDateTime = instant.toLocalDateTime(TimeZone.UTC)

        // Convert the timestamp to LocalDateTime using the system's default timezone.
        val localTimestampSegment: LocalDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

        // Extract the sequence number segment.
        val sequenceSegment: Long = id.toLong() and MAX_SEQUENCE.toLong()

        return SnowflakeData(
            machineId = machineIdSegment.toInt(),
            sequence = sequenceSegment,
            utc = utcTimestampSegment,
            local = localTimestampSegment
        )
    }

    /**
     * Returns a more robust current timestamp in milliseconds.
     * This method combines `System.currentTimeMillis()` and `System.nanoTime()`
     * to mitigate the impact of system clock adjustments.
     * `System.nanoTime()` is used for its monotonic properties, ensuring the measured
     * elapsed time does not decrease even if the system clock is adjusted.
     * The initial system time (`timestampEpoch`) captured at application startup
     * is combined with the elapsed time since then, calculated using `System.nanoTime()`,
     * to produce a stable and increasing timestamp.
     */
    private fun newTimestamp(): Long {
        val nanoTimeDiff: Long = System.nanoTime() - nanoTimeStart
        return timestampEpoch + nanoTimeDiff.nanoseconds.inWholeMilliseconds
    }
}
