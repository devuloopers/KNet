package com.devuloopers.knet.ui.core.foundation.time

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Kotlin Multiplatform date and time formatting used by presentation modules.
 *
 * Epoch acquisition and instant arithmetic use `kotlin.time`. Calendar conversion uses
 * `kotlinx-datetime` because the Kotlin standard library deliberately does not model time zones or
 * local calendar values.
 */
object KNetDateTime {

    private val displayTimeZone: TimeZone = TimeZone.currentSystemDefault()

    /** Returns the current local date as an ISO-8601 key. */
    fun currentDateKey(): String = Clock.System.now().toLocalDateTime(displayTimeZone).date.toString()

    /** Returns the local ISO-8601 date key for [epochMillis]. */
    fun dateKey(epochMillis: Long): String = dateKey(epochMillis, displayTimeZone)

    /** Returns local `HH:mm:ss` time, optionally including three millisecond digits. */
    fun time(epochMillis: Long, includeMilliseconds: Boolean = false): String =
        time(epochMillis, includeMilliseconds, displayTimeZone)

    /** Returns local `HH:mm:ss - dd/MM` time and compact date. */
    fun timeAndDayMonth(epochMillis: Long): String = timeAndDayMonth(epochMillis, displayTimeZone)

    internal fun dateKey(epochMillis: Long, timeZone: TimeZone): String =
        localDateTime(epochMillis, timeZone).date.toString()

    internal fun time(epochMillis: Long, includeMilliseconds: Boolean, timeZone: TimeZone): String {
        val dateTime = localDateTime(epochMillis, timeZone)
        return buildString(capacity = if (includeMilliseconds) 12 else 8) {
            appendTime(dateTime)
            if (includeMilliseconds) {
                append('.')
                append((dateTime.nanosecond / NANOS_PER_MILLISECOND).fixedWidth(3))
            }
        }
    }

    internal fun timeAndDayMonth(epochMillis: Long, timeZone: TimeZone): String {
        val dateTime = localDateTime(epochMillis, timeZone)
        return buildString(capacity = 16) {
            appendTime(dateTime)
            append(" - ")
            append(dateTime.day.fixedWidth(2))
            append('/')
            append(dateTime.month.number.fixedWidth(2))
        }
    }

    private fun localDateTime(epochMillis: Long, timeZone: TimeZone): LocalDateTime = Instant
        .fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(timeZone)

    private fun StringBuilder.appendTime(dateTime: LocalDateTime) {
        append(dateTime.hour.fixedWidth(2))
        append(':')
        append(dateTime.minute.fixedWidth(2))
        append(':')
        append(dateTime.second.fixedWidth(2))
    }

    private fun Int.fixedWidth(width: Int): String = toString().padStart(width, '0')

    private const val NANOS_PER_MILLISECOND: Int = 1_000_000
}
