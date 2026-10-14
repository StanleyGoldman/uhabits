/*
 * Copyright (C) 2016-2025 Álinson Santos Xavier <git@axavier.org>
 *
 * This file is part of Loop Habit Tracker.
 *
 * Loop Habit Tracker is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Loop Habit Tracker is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.isoron.platform.time

import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Dates before 2000 are negative day counts in [LocalDate]. Upstream crashes
 * (iSoron/uhabits#1690, #2229) came from dates at or before the Unix epoch.
 */
class PreEpochDatesTest {

    @Test
    fun everyDayFrom1900To2000RoundTripsThroughDaysSince2000() {
        var expected = java.time.LocalDate.of(1900, 1, 1)
        val last = java.time.LocalDate.of(2000, 12, 31)
        while (!expected.isAfter(last)) {
            val built = LocalDate(expected.year, expected.monthValue, expected.dayOfMonth)
            val date = LocalDate(built.daysSince2000)
            val label = expected.toString()
            assertEquals(expected.year, date.year, label)
            assertEquals(expected.monthValue, date.month, label)
            assertEquals(expected.dayOfMonth, date.day, label)
            assertEquals(expected.dayOfWeek.value % 7, date.dayOfWeek.daysSinceSunday, label)
            expected = expected.plusDays(1)
        }
    }

    @Test
    fun unixEpochIsFirstOfJanuary1970() {
        val date = LocalDate.fromUnixTime(0)
        assertEquals(LocalDate(1970, 1, 1), date)
        assertEquals(0L, date.unixTime)
    }

    @Test
    fun millisecondBeforeUnixEpochIsLastDayOf1969() {
        assertEquals(LocalDate(1969, 12, 31), LocalDate.fromUnixTime(-1))
    }

    @Test
    fun frequencyChartMonthHelpersWorkFrom1960To1971() {
        val formatter = JavaLocalDateFormatter(Locale.US)
        for (year in 1960..1971) {
            for (month in 1..12) {
                val label = "$year-$month"
                val yearMonth = YearMonth.of(year, month)
                // Index 0 is Saturday, 1 is Sunday, ..., 6 is Friday.
                val expected = Array(7) { 0 }
                for (day in 1..yearMonth.lengthOfMonth()) {
                    val weekday = yearMonth.atDay(day).dayOfWeek.value % 7
                    expected[(weekday + 1) % 7] += 1
                }
                val startOfMonth = LocalDate(year, month, 1)
                assertEquals(
                    expected.toList(),
                    countWeekdayOccurrencesInMonth(startOfMonth).toList(),
                    label
                )
                assertEquals(
                    Month.of(month).getDisplayName(TextStyle.SHORT, Locale.US),
                    formatter.shortMonthName(startOfMonth),
                    label
                )
            }
        }
    }
}
