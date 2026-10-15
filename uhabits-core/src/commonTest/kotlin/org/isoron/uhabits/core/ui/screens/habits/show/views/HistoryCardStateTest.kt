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

import org.isoron.platform.time.DayOfWeek
import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.BaseUnitTest
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Frequency
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitType
import org.isoron.uhabits.core.models.NumericalHabitType
import org.isoron.uhabits.core.models.NumericalHabitType.AT_LEAST
import org.isoron.uhabits.core.models.NumericalHabitType.AT_MOST
import org.isoron.uhabits.core.models.TargetPeriodMode
import org.isoron.uhabits.core.ui.views.HistoryChart.Square
import org.isoron.uhabits.core.ui.views.LightTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The squares the History card and the History widget draw for a weekly or
 * monthly measurable habit, each day carrying its period's verdict. Today is
 * Sunday 2015-01-25 and weeks start on Sunday, so the week of 18 January is
 * closed and the week of 25 January is open.
 */
class HistoryCardStateTest : BaseUnitTest() {

    @Test
    fun testMetWeek_enteredDaysOn_emptyDaysDimmed() {
        val h = weeklyHabit(AT_LEAST, 10.0)
        h.withAmountOn(LocalDate(2015, 1, 19), 4.0)
        h.withAmountOn(LocalDate(2015, 1, 21), 6.0)
        val squares = squaresOf(h)
        assertEquals(Square.ON, squares(LocalDate(2015, 1, 19)))
        assertEquals(Square.ON, squares(LocalDate(2015, 1, 21)))
        assertEquals(Square.DIMMED, squares(LocalDate(2015, 1, 20)))
        assertEquals(Square.DIMMED, squares(LocalDate(2015, 1, 24)))
    }

    @Test
    fun testMissedWeek_enteredDaysGrey_emptyDaysOff() {
        val h = weeklyHabit(AT_LEAST, 10.0)
        h.withAmountOn(LocalDate(2015, 1, 19), 4.0)
        val squares = squaresOf(h)
        assertEquals(Square.GREY, squares(LocalDate(2015, 1, 19)))
        assertEquals(Square.OFF, squares(LocalDate(2015, 1, 20)))
    }

    @Test
    fun testOpenMonth_daysSoFarOutlined() {
        val h = monthlyHabit(AT_LEAST, 100.0)
        h.withAmountOn(LocalDate(2015, 1, 10), 5.0)
        val squares = squaresOf(h)
        assertEquals(Square.DIMMED_OUTLINED, squares(LocalDate(2015, 1, 10)))
        assertEquals(Square.OFF_OUTLINED, squares(LocalDate(2015, 1, 11)))
        assertEquals(Square.OFF_OUTLINED, squares(getToday()))
    }

    @Test
    fun testSkippedDay_hatchedInMetAndOpenPeriods() {
        val h = weeklyHabit(AT_LEAST, 10.0)
        h.withAmountOn(LocalDate(2015, 1, 19), 10.0)
        h.withSkipOn(LocalDate(2015, 1, 20))
        h.withSkipOn(getToday())
        val squares = squaresOf(h)
        assertEquals(Square.HATCHED, squares(LocalDate(2015, 1, 20)), "met")
        assertEquals(Square.HATCHED, squares(getToday()), "open")
    }

    @Test
    fun testSkippedDay_greyHatchedInMissedPeriod() {
        val h = weeklyHabit(AT_LEAST, 10.0)
        h.withAmountOn(LocalDate(2015, 1, 19), 4.0)
        h.withSkipOn(LocalDate(2015, 1, 20))
        assertEquals(Square.GREY_HATCHED, squaresOf(h)(LocalDate(2015, 1, 20)))
    }

    @Test
    fun testAtMost_redCornerOnTheDayTheTotalWentOverTheCap() {
        val h = weeklyHabit(AT_MOST, 4.0)
        h.withAmountOn(LocalDate(2015, 1, 19), 2.0)
        h.withAmountOn(LocalDate(2015, 1, 20), 2.0)
        h.withAmountOn(LocalDate(2015, 1, 21), 1.0)
        h.withAmountOn(LocalDate(2015, 1, 22), 1.0)
        val squares = squaresOf(h)
        assertEquals(Square.GREY, squares(LocalDate(2015, 1, 20)), "at the cap")
        assertEquals(Square.GREY_OVER_CAP, squares(LocalDate(2015, 1, 21)), "over the cap")
        assertEquals(Square.GREY, squares(LocalDate(2015, 1, 22)), "already over")
    }

    @Test
    fun testAtMost_skipLowersTheCapForTheRedCorner() {
        // One skip shrinks the cap of 4 to 4 × 6/7, about 3.4.
        val h = weeklyHabit(AT_MOST, 4.0)
        h.withSkipOn(LocalDate(2015, 1, 11))
        h.withAmountOn(LocalDate(2015, 1, 12), 2.0)
        h.withAmountOn(LocalDate(2015, 1, 13), 2.0)
        assertEquals(Square.GREY_OVER_CAP, squaresOf(h)(LocalDate(2015, 1, 13)))
    }

    @Test
    fun testRollingWeek_eachDayJudgedByItsOwnWindow() {
        val h = weeklyHabit(AT_LEAST, 10.0)
        h.targetPeriodMode = TargetPeriodMode.ROLLING
        h.withAmountOn(LocalDate(2015, 1, 15), 10.0)
        val squares = squaresOf(h)
        assertEquals(Square.ON, squares(LocalDate(2015, 1, 15)))
        assertEquals(Square.DIMMED, squares(LocalDate(2015, 1, 21)), "last day whose window holds it")
        assertEquals(Square.OFF, squares(LocalDate(2015, 1, 22)), "window holds nothing")
        assertEquals(Square.OFF_OUTLINED, squares(getToday()))
    }

    @Test
    fun testRollingWeek_atMost_hasNoRedCorner() {
        val h = weeklyHabit(AT_MOST, 4.0)
        h.targetPeriodMode = TargetPeriodMode.ROLLING
        h.withAmountOn(LocalDate(2015, 1, 20), 5.0)
        assertEquals(Square.GREY, squaresOf(h)(LocalDate(2015, 1, 20)))
    }

    @Test
    fun testCalendarWeek_daysBeforeTheOldestEntryCarryItsPeriod() {
        val h = weeklyHabit(AT_LEAST, 10.0)
        h.withAmountOn(LocalDate(2015, 1, 21), 10.0)
        assertEquals(Square.DIMMED, squaresOf(h)(LocalDate(2015, 1, 18)))
    }

    @Test
    fun testAllSkippedWeek_hatched() {
        val h = weeklyHabit(AT_LEAST, 10.0)
        h.withAmountOn(LocalDate(2015, 1, 12), 10.0)
        for (day in 18..24) h.withSkipOn(LocalDate(2015, 1, day))
        assertEquals(Square.HATCHED, squaresOf(h)(LocalDate(2015, 1, 20)))
    }

    @Test
    fun testAtMost_closedWeekUnderTheCap_emptyDaysDimmed() {
        // A weekly period adds 0 for an empty day whatever assume none says.
        for (assumeNone in listOf(true, false)) {
            val h = weeklyHabit(AT_MOST, 4.0)
            h.assumeNone = assumeNone
            h.withAmountOn(LocalDate(2015, 1, 19), 3.0)
            val squares = squaresOf(h)
            assertEquals(Square.ON, squares(LocalDate(2015, 1, 19)), "assume none $assumeNone")
            assertEquals(Square.DIMMED, squares(LocalDate(2015, 1, 20)), "assume none $assumeNone")
        }
    }

    @Test
    fun testClosedMonthStoredAsOneIn31_judgedAsTheCalendarMonth() {
        val h = measurableHabit(Frequency(1, 31), AT_LEAST, 100.0)
        h.withAmountOn(LocalDate(2014, 12, 1), 60.0)
        h.withAmountOn(LocalDate(2014, 12, 31), 40.0)
        val squares = squaresOf(h)
        assertEquals(Square.ON, squares(LocalDate(2014, 12, 31)))
        assertEquals(Square.DIMMED, squares(LocalDate(2014, 12, 15)))
        assertEquals(Square.OFF_OUTLINED, squares(LocalDate(2015, 1, 1)), "January is open")
    }

    private fun weeklyHabit(targetType: NumericalHabitType, target: Double) =
        measurableHabit(Frequency.WEEKLY, targetType, target)

    private fun monthlyHabit(targetType: NumericalHabitType, target: Double) =
        measurableHabit(Frequency(1, 30), targetType, target)

    private fun measurableHabit(
        frequency: Frequency,
        targetType: NumericalHabitType,
        target: Double
    ): Habit {
        val h = modelFactory.buildHabit()
        h.type = HabitType.NUMERICAL
        h.frequency = frequency
        h.targetType = targetType
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

    /** The square drawn on a date, or the default square before the series. */
    private fun squaresOf(h: Habit): (LocalDate) -> Square {
        val state = HistoryCardPresenter.buildState(h, DayOfWeek.SUNDAY, LightTheme())
        return { date -> state.series.getOrElse(date.daysUntil(getToday())) { state.defaultSquare } }
    }
}
