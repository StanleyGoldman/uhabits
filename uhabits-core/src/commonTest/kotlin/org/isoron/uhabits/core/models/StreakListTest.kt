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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StreakListTest : BaseUnitTest() {
    private lateinit var habit: Habit
    private lateinit var streaks: StreakList
    private lateinit var today: LocalDate

    @BeforeTest
    override fun setUp() {
        super.setUp()
        habit = fixtures.createLongHabit()
        habit.frequency = Frequency.DAILY
        habit.recompute()
        streaks = habit.streaks
        today = getToday()
    }

    @Test
    fun testGetBest() {
        var best = streaks.getBest(4)
        assertEquals(4, best.size)
        assertEquals(4, best[0].length)
        assertEquals(3, best[1].length)
        assertEquals(5, best[2].length)
        assertEquals(6, best[3].length)
        best = streaks.getBest(2)
        assertEquals(2, best.size)
        assertEquals(5, best[0].length)
        assertEquals(6, best[1].length)
    }

    @Test
    fun testGetBest_withUnknowns() {
        habit.originalEntries.clear()
        habit.originalEntries.add(Entry(today, Entry.YES_MANUAL))
        habit.originalEntries.add(Entry(today.minus(5), Entry.NO))
        habit.recompute()
        val best = streaks.getBest(5)
        assertEquals(1, best.size)
        assertEquals(1, best[0].length)
    }

    // Today is Sunday 2015-01-25; weeks run Sunday to Saturday.

    @Test
    fun testCalendarWeek_metPeriodsJoinWholeAndAnOpenPeriodIsPending() {
        val h = buildMeasurableHabit(NumericalHabitType.AT_LEAST, 10.0, Frequency.WEEKLY)
        h.withAmountOn(LocalDate(2015, 1, 12), 10.0)
        h.withAmountOn(LocalDate(2015, 1, 20), 10.0)
        h.withAmountOn(today, 4.0)
        assertEquals(
            listOf(LocalDate(2015, 1, 11)..LocalDate(2015, 1, 24)),
            h.streaks.getBest(5).map { it.start..it.end },
            "the open week is left out"
        )

        h.withAmountOn(today, 10.0)
        assertEquals(
            listOf(LocalDate(2015, 1, 11)..LocalDate(2015, 1, 31)),
            h.streaks.getBest(5).map { it.start..it.end },
            "once met, the whole week joins"
        )
    }

    @Test
    fun testCalendarWeek_aShortPeriodEndsTheStreakAtThePreviousPeriod() {
        val h = buildMeasurableHabit(NumericalHabitType.AT_LEAST, 10.0, Frequency.WEEKLY)
        h.withAmountOn(LocalDate(2015, 1, 5), 10.0)
        h.withAmountOn(LocalDate(2015, 1, 12), 10.0)
        h.withAmountOn(LocalDate(2015, 1, 20), 9.0)
        h.withAmountOn(today, 10.0)
        assertEquals(
            listOf(
                LocalDate(2015, 1, 25)..LocalDate(2015, 1, 31),
                LocalDate(2015, 1, 4)..LocalDate(2015, 1, 17)
            ),
            h.streaks.getBest(5).map { it.start..it.end }
        )
    }

    @Test
    fun testCalendarWeek_anAllSkippedWeekBridgesTheStreak() {
        val h = buildMeasurableHabit(NumericalHabitType.AT_LEAST, 10.0, Frequency.WEEKLY)
        h.withAmountOn(LocalDate(2015, 1, 5), 10.0)
        for (day in 11..17) h.originalEntries.add(Entry(LocalDate(2015, 1, day), Entry.SKIP))
        h.withAmountOn(LocalDate(2015, 1, 20), 10.0)
        assertEquals(
            listOf(LocalDate(2015, 1, 4)..LocalDate(2015, 1, 24)),
            h.streaks.getBest(5).map { it.start..it.end }
        )
    }

    @Test
    fun testRollingWeek_eachDayJudgedByItsWindowUpToToday() {
        val h = buildMeasurableHabit(NumericalHabitType.AT_LEAST, 10.0, Frequency.WEEKLY)
        h.targetPeriodMode = TargetPeriodMode.ROLLING
        h.withAmountOn(LocalDate(2015, 1, 20), 5.0)
        h.withAmountOn(LocalDate(2015, 1, 21), 5.0)
        assertEquals(
            listOf(LocalDate(2015, 1, 21)..today),
            h.streaks.getBest(5).map { it.start..it.end },
            "windows ending 2015-01-21..26 hold both amounts; the days after today are still to come"
        )
    }

    @Test
    fun testPeriods_countMetCalendarPeriods() {
        val weekly = buildMeasurableHabit(NumericalHabitType.AT_LEAST, 10.0, Frequency.WEEKLY)
        weekly.withAmountOn(LocalDate(2015, 1, 12), 10.0)
        weekly.withAmountOn(LocalDate(2015, 1, 20), 10.0)
        weekly.withAmountOn(today, 4.0)
        assertEquals(
            listOf(Pair(2, PeriodUnit.WEEK)),
            weekly.streaks.getBest(5).map { Pair(it.periodCount, it.periodUnit) },
            "the open week isn't counted"
        )

        val monthly = buildMeasurableHabit(NumericalHabitType.AT_LEAST, 10.0, Frequency(1, 30))
        monthly.withAmountOn(LocalDate(2014, 12, 10), 10.0)
        assertEquals(
            listOf(Pair(1, PeriodUnit.MONTH)),
            monthly.streaks.getBest(5).map { Pair(it.periodCount, it.periodUnit) }
        )

        weekly.targetPeriodMode = TargetPeriodMode.ROLLING
        weekly.recompute()
        assertEquals(
            null,
            weekly.streaks.getBest(5).map { it.periodUnit }.distinct().single(),
            "rolling streaks have no periods"
        )
    }

    @Test
    fun testAtMost_followsAssumeNone() {
        val h = buildMeasurableHabit(NumericalHabitType.AT_MOST, 2.0, Frequency.DAILY)
        h.withAmountOn(LocalDate(2015, 1, 20), 1.0)
        h.withAmountOn(LocalDate(2015, 1, 24), 3.0)
        assertEquals(
            listOf(LocalDate(2015, 1, 20)..LocalDate(2015, 1, 23)),
            h.streaks.getBest(5).map { it.start..it.end },
            "on: days with nothing entered are met"
        )

        h.assumeNone = false
        h.recompute()
        assertEquals(
            listOf(LocalDate(2015, 1, 20)..LocalDate(2015, 1, 20)),
            h.streaks.getBest(5).map { it.start..it.end },
            "off: days with nothing entered are missed"
        )
    }

    private fun buildMeasurableHabit(
        targetType: NumericalHabitType,
        target: Double,
        frequency: Frequency
    ): Habit {
        val h = modelFactory.buildHabit()
        h.type = HabitType.NUMERICAL
        h.targetType = targetType
        h.targetValue = target
        h.frequency = frequency
        h.recompute()
        return h
    }

    private fun Habit.withAmountOn(date: LocalDate, amount: Double) {
        originalEntries.add(Entry(date, Entry.AMOUNT, (amount * 1000).toInt()))
        recompute()
    }
}
