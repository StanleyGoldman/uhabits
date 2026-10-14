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

package org.isoron.uhabits.core.models

import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.BaseUnitTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A device whose clock reads 1970 can record entries at the Unix epoch
 * (iSoron/uhabits#1690). Such entries must load and compute like any other.
 */
class PreEpochEntriesTest : BaseUnitTest() {
    private val newYearsEve1969 = LocalDate(1969, 12, 31) // Wednesday
    private val epochDay = LocalDate(1970, 1, 1) // Thursday

    @Test
    fun dailyYesOrNoHabit() {
        val habit = yesOrNoHabit(Frequency.DAILY)
        assertEquals(
            listOf(Streak(newYearsEve1969, epochDay)),
            habit.streaks.getBest(1)
        )
        assertWeekdayFrequencyAndCompletedToday(habit, value = 1)
    }

    @Test
    fun threeTimesPerWeekYesOrNoHabit() {
        val habit = yesOrNoHabit(Frequency.THREE_TIMES_PER_WEEK)
        assertStreakSpansBothPreEpochDays(habit)
        assertWeekdayFrequencyAndCompletedToday(habit, value = 1)
    }

    @Test
    fun oncePerMonthYesOrNoHabit() {
        val habit = yesOrNoHabit(Frequency(1, 30))
        assertStreakSpansBothPreEpochDays(habit)
        assertWeekdayFrequencyAndCompletedToday(habit, value = 1)
    }

    @Test
    fun weeklyAtLeastMeasurableHabit() {
        val habit = modelFactory.buildHabit().apply {
            type = HabitType.NUMERICAL
            targetType = NumericalHabitType.AT_LEAST
            targetValue = 2.0
            frequency = Frequency.WEEKLY
        }
        for (date in listOf(newYearsEve1969, epochDay, getToday())) {
            habit.originalEntries.add(Entry(date, 3000))
        }
        habit.recompute()
        assertStreakSpansBothPreEpochDays(habit)
        assertWeekdayFrequencyAndCompletedToday(habit, value = 3000)
    }

    private fun yesOrNoHabit(frequency: Frequency): Habit {
        val habit = modelFactory.buildHabit()
        habit.frequency = frequency
        for (date in listOf(newYearsEve1969, epochDay, getToday())) {
            habit.originalEntries.add(Entry(date, Entry.YES_MANUAL))
        }
        habit.recompute()
        return habit
    }

    private fun assertStreakSpansBothPreEpochDays(habit: Habit) {
        val streaks = habit.streaks.getBest(10)
        assertTrue(
            streaks.any { !it.start.isNewerThan(newYearsEve1969) && !it.end.isOlderThan(epochDay) },
            "no streak spans $newYearsEve1969..$epochDay in $streaks"
        )
    }

    private fun assertWeekdayFrequencyAndCompletedToday(habit: Habit, value: Int) {
        // Index 0 is Saturday, so Wednesday is 4 and Thursday is 5.
        val frequency = habit.originalEntries.computeWeekdayFrequency(habit.isNumerical)
        assertEquals(
            listOf(0, 0, 0, 0, value, 0, 0),
            frequency[LocalDate(1969, 12, 1)]?.toList()
        )
        assertEquals(
            listOf(0, 0, 0, 0, 0, value, 0),
            frequency[LocalDate(1970, 1, 1)]?.toList()
        )
        assertTrue(habit.isCompletedToday())
    }
}
