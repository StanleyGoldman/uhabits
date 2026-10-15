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

package org.isoron.uhabits.core.ui.screens.habits.show.views

import org.isoron.platform.time.LocalDate
import org.isoron.uhabits.core.BaseUnitTest
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Frequency
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitType
import org.isoron.uhabits.core.models.NumericalHabitType
import org.isoron.uhabits.core.models.TargetPeriodMode
import org.isoron.uhabits.core.ui.views.LightTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The rows of the Target card. Today is Sunday 2015-01-25 and weeks start on
 * Sunday, so this calendar week holds only today.
 */
class TargetCardStateTest : BaseUnitTest() {
    private val SUNDAY = 1 // the 1-based weekday number TargetCardPresenter takes

    @Test
    fun testRollingWeek_firstRowIsTheLast7Days() {
        val h = measurableHabit(Frequency.WEEKLY, 10.0)
        h.targetPeriodMode = TargetPeriodMode.ROLLING
        h.withAmountOn(LocalDate(2015, 1, 18), 1.0)
        h.withAmountOn(LocalDate(2015, 1, 19), 4.0)
        val state = stateOf(h)
        assertEquals(TargetInterval.LAST_7_DAYS, state.intervals[0])
        assertEquals(4.0, state.values[0], "Jan 19 to 25, not the calendar week")
        assertEquals(10.0, state.targets[0])
    }

    @Test
    fun testRollingMonth_firstRowIsTheLast30Days_skipScalesTheTarget() {
        val h = measurableHabit(Frequency(1, 30), 90.0)
        h.targetPeriodMode = TargetPeriodMode.ROLLING
        h.withAmountOn(LocalDate(2014, 12, 27), 20.0)
        h.withSkipOn(LocalDate(2015, 1, 10))
        h.withSkipOn(LocalDate(2015, 1, 11))
        h.withSkipOn(LocalDate(2015, 1, 12))
        val state = stateOf(h)
        assertEquals(TargetInterval.LAST_30_DAYS, state.intervals[0])
        assertEquals(20.0, state.values[0], "Dec 27 to Jan 25, not the calendar month")
        assertEquals(81.0, state.targets[0], 1e-9, "27 of 30 days count")
    }

    @Test
    fun testCalendarWeek_firstRowIsTheWeek() {
        val h = measurableHabit(Frequency.WEEKLY, 10.0)
        h.withAmountOn(LocalDate(2015, 1, 19), 4.0)
        val state = stateOf(h)
        assertEquals(TargetInterval.WEEK, state.intervals[0])
        assertEquals(0.0, state.values[0], "the week of Jan 25 holds only today")
    }

    private fun measurableHabit(frequency: Frequency, target: Double): Habit {
        val h = modelFactory.buildHabit()
        h.type = HabitType.NUMERICAL
        h.frequency = frequency
        h.targetType = NumericalHabitType.AT_LEAST
        h.targetValue = target
        h.recompute()
        return h
    }

    private fun Habit.withAmountOn(date: LocalDate, amount: Double) {
        originalEntries.add(Entry(date, Entry.AMOUNT, (amount * 1000).toInt()))
        recompute()
    }

    private fun Habit.withSkipOn(date: LocalDate) {
        originalEntries.add(Entry(date, Entry.SKIP, null))
        recompute()
    }

    private fun stateOf(h: Habit) = TargetCardPresenter.buildState(h, firstWeekday = SUNDAY, theme = LightTheme())
}
