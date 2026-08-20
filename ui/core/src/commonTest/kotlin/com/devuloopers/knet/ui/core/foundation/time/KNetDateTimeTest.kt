package com.devuloopers.knet.ui.core.foundation.time

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class KNetDateTimeTest {

    @Test
    fun `formats epoch with Kotlin UTC calendar values`() {
        assertEquals("1970-01-01", KNetDateTime.dateKey(epochMillis = 0L, timeZone = TimeZone.UTC))
        assertEquals(
            "00:00:00.000",
            KNetDateTime.time(epochMillis = 0L, includeMilliseconds = true, timeZone = TimeZone.UTC),
        )
        assertEquals("00:00:00 - 01/01", KNetDateTime.timeAndDayMonth(0L, TimeZone.UTC))
        assertEquals("1 Jan 1970", KNetDateTime.humanDate(0L, TimeZone.UTC))
    }

    @Test
    fun `preserves exactly three millisecond digits`() {
        assertEquals(
            "00:00:00.007",
            KNetDateTime.time(epochMillis = 7L, includeMilliseconds = true, timeZone = TimeZone.UTC),
        )
    }
}
