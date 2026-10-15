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

import org.isoron.platform.time.DayOfWeek
import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.getToday
import org.isoron.platform.time.setFirstWeekday
import org.isoron.platform.time.setToday
import org.isoron.uhabits.core.BaseUnitTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HabitTest : BaseUnitTest() {

    @BeforeTest
    override fun setUp() {
        super.setUp()
    }

    @Test
    fun testUuidGeneration() {
        val uuid1 = modelFactory.buildHabit().uuid!!
        val uuid2 = modelFactory.buildHabit().uuid!!
        assertNotEquals(uuid1, uuid2)
    }

    @Test
    fun test_copyAttributes() {
        val model = modelFactory.buildHabit()
        model.isArchived = true
        model.color = PaletteColor(0)
        model.frequency = Frequency(10, 20)
        model.reminder = Reminder(8, 30, WeekdayList(1))
        val habit = modelFactory.buildHabit()
        habit.copyFrom(model)
        assertEquals(habit.isArchived, model.isArchived)
        assertEquals(model.isArchived, habit.isArchived)
        assertEquals(model.color, habit.color)
        assertEquals(model.frequency, habit.frequency)
        assertEquals(model.reminder, habit.reminder)
    }

    @Test
    fun test_hasReminder() {
        val h = modelFactory.buildHabit()
        assertEquals(false, h.hasReminder())
        h.reminder = Reminder(8, 30, WeekdayList.EVERY_DAY)
        assertEquals(true, h.hasReminder())
    }

    @Test
    fun test_todayStatus_yesOrNo() {
        val h = modelFactory.buildHabit()
        h.recompute()
        assertEquals(DayStatus.OPEN, h.todayStatus(), "unknown")
        h.withEntryToday(Entry.YES_MANUAL)
        assertEquals(DayStatus.MET, h.todayStatus(), "yes (manual)")
        h.withEntryToday(Entry.SKIP)
        assertEquals(DayStatus.MET, h.todayStatus(), "skip")
        h.withEntryToday(Entry.NO)
        assertEquals(DayStatus.MISSED, h.todayStatus(), "no")
    }

    @Test
    fun test_todayStatus_yesOrNo_yesAutoIsMet() {
        val h = modelFactory.buildHabit()
        h.frequency = Frequency.WEEKLY
        h.originalEntries.add(Entry(getToday().minus(1), Entry.YES_MANUAL))
        h.recompute()
        assertEquals(Entry.YES_AUTO, h.computedEntries.get(getToday()).state, "setup")
        assertEquals(DayStatus.MET, h.todayStatus())
    }

    @Test
    fun test_todayStatus_atLeast() {
        val h = buildMeasurableHabit(NumericalHabitType.AT_LEAST, 10.0)
        assertEquals(DayStatus.OPEN, h.todayStatus(), "unknown")
        h.withAmountToday(4.0)
        assertEquals(DayStatus.OPEN, h.todayStatus(), "below the target")
        h.withAmountToday(10.0)
        assertEquals(DayStatus.MET, h.todayStatus(), "at the target")
        h.withAmountToday(12.0)
        assertEquals(DayStatus.MET, h.todayStatus(), "above the target")
    }

    @Test
    fun test_todayStatus_atMost() {
        val h = buildMeasurableHabit(NumericalHabitType.AT_MOST, 5.0)
        assertEquals(DayStatus.OPEN, h.todayStatus(), "unknown")
        h.withAmountToday(0.0)
        assertEquals(DayStatus.OPEN, h.todayStatus(), "zero")
        h.withAmountToday(1.0)
        assertEquals(DayStatus.OPEN, h.todayStatus(), "under the cap")
        h.withAmountToday(5.0)
        assertEquals(DayStatus.OPEN, h.todayStatus(), "at the cap")
        h.withAmountToday(6.0)
        assertEquals(DayStatus.MISSED, h.todayStatus(), "over the cap")
    }

    @Test
    fun test_todayStatus_atLeastZero_unknownIsOpen() {
        val h = buildMeasurableHabit(NumericalHabitType.AT_LEAST, 0.0)
        assertEquals(DayStatus.OPEN, h.todayStatus())
    }

    @Test
    fun test_todayStatus_measurableSkipIsMet() {
        val atLeast = buildMeasurableHabit(NumericalHabitType.AT_LEAST, 10.0)
        atLeast.withEntryToday(Entry.SKIP)
        assertEquals(DayStatus.MET, atLeast.todayStatus(), "at least")
        val atMost = buildMeasurableHabit(NumericalHabitType.AT_MOST, 5.0)
        atMost.withEntryToday(Entry.SKIP)
        assertEquals(DayStatus.MET, atMost.todayStatus(), "at most")
    }

    @Test
    fun test_todayStatus_calendarWeek_startsOnFirstDayOfWeek() {
        // Today is Sunday 2015-01-25.
        val h = buildMeasurableHabit(NumericalHabitType.AT_LEAST, 10.0)
        h.frequency = Frequency.WEEKLY
        h.withAmountOn(LocalDate(2015, 1, 19), 4.0)
        h.withAmountOn(LocalDate(2015, 1, 21), 6.0)

        setFirstWeekday { DayOfWeek.MONDAY }
        assertEquals(DayStatus.MET, h.todayStatus(), "Monday to Sunday holds both amounts")
        setFirstWeekday { DayOfWeek.SUNDAY }
        assertEquals(DayStatus.OPEN, h.todayStatus(), "the week starting today is empty")
    }

    @Test
    fun test_todayStatus_calendarMonth_usesItsOwnLength() {
        setToday(LocalDate(2015, 2, 28))
        val h = buildMeasurableHabit(NumericalHabitType.AT_LEAST, 30.0)
        h.frequency = Frequency(1, 30)
        h.withAmountOn(LocalDate(2015, 1, 31), 20.0)
        h.withAmountOn(LocalDate(2015, 2, 1), 10.0)
        h.withAmountOn(LocalDate(2015, 2, 28), 10.0)
        assertEquals(DayStatus.OPEN, h.todayStatus(), "January's amount is not in February")
        h.withAmountOn(LocalDate(2015, 2, 14), 10.0)
        assertEquals(DayStatus.MET, h.todayStatus(), "February reaches the target")
    }

    @Test
    fun test_statusOn_atLeast_closedPeriod() {
        val h = buildMeasurableHabit(NumericalHabitType.AT_LEAST, 10.0)
        h.frequency = Frequency.WEEKLY
        // Sunday-first weeks: 2015-01-11..17 and 2015-01-18..24.
        h.withAmountOn(LocalDate(2015, 1, 12), 10.0)
        h.withAmountOn(LocalDate(2015, 1, 20), 6.0)
        assertEquals(DayStatus.MET, h.statusOn(LocalDate(2015, 1, 15)), "reached the target")
        assertEquals(DayStatus.MISSED, h.statusOn(LocalDate(2015, 1, 20)), "closed below the target")
        assertEquals(DayStatus.OPEN, h.statusOn(getToday()), "this week is still open")
    }

    @Test
    fun test_statusOn_atMost() {
        val h = buildMeasurableHabit(NumericalHabitType.AT_MOST, 4.0)
        h.frequency = Frequency.WEEKLY
        // Sunday-first weeks: 2015-01-11..17, 2015-01-18..24, and 2015-01-25.. holding today.
        h.withAmountOn(LocalDate(2015, 1, 12), 4.0)
        h.withAmountOn(LocalDate(2015, 1, 19), 3.0)
        h.withAmountOn(LocalDate(2015, 1, 20), 2.0)
        assertEquals(DayStatus.MET, h.statusOn(LocalDate(2015, 1, 12)), "closed at the cap")
        assertEquals(DayStatus.MISSED, h.statusOn(LocalDate(2015, 1, 19)), "went over the cap")
        assertEquals(DayStatus.OPEN, h.todayStatus(), "under the cap, never met mid-period")
        h.withAmountToday(5.0)
        assertEquals(DayStatus.MISSED, h.todayStatus(), "over the cap mid-period")
    }

    @Test
    fun test_statusOn_rollingWeek() {
        val h = buildMeasurableHabit(NumericalHabitType.AT_LEAST, 10.0)
        h.frequency = Frequency.WEEKLY
        h.targetPeriodMode = TargetPeriodMode.ROLLING
        h.withAmountOn(LocalDate(2015, 1, 20), 6.0)
        h.withAmountOn(LocalDate(2015, 1, 23), 4.0)
        assertEquals(DayStatus.MISSED, h.statusOn(LocalDate(2015, 1, 21)), "2015-01-15..21 holds 6")
        assertEquals(DayStatus.MET, h.statusOn(LocalDate(2015, 1, 24)), "2015-01-18..24 holds 10")
        assertEquals(DayStatus.MET, h.todayStatus(), "2015-01-19..25 holds 10")
        assertEquals(DayStatus.MISSED, h.statusOn(LocalDate(2015, 1, 13)), "2015-01-07..13 is empty")
    }

    @Test
    fun test_statusOn_rollingMonth() {
        val h = buildMeasurableHabit(NumericalHabitType.AT_MOST, 10.0)
        h.frequency = Frequency(1, 30)
        h.targetPeriodMode = TargetPeriodMode.ROLLING
        h.withAmountOn(LocalDate(2014, 12, 26), 6.0)
        h.withAmountOn(LocalDate(2015, 1, 10), 6.0)
        assertEquals(DayStatus.MISSED, h.statusOn(LocalDate(2015, 1, 24)), "2014-12-26..2015-01-24 holds 12")
        assertEquals(DayStatus.OPEN, h.todayStatus(), "2014-12-27..2015-01-25 holds 6")
    }

    @Test
    fun test_statusOn_skipScalesTheTarget() {
        val h = buildMeasurableHabit(NumericalHabitType.AT_LEAST, 10.0)
        h.frequency = Frequency.WEEKLY
        // Week 2015-01-18..24 with one skip: the target scales to 10 × 6/7 ≈ 8.571.
        h.withEntryOn(LocalDate(2015, 1, 19), Entry.SKIP)
        h.withAmountOn(LocalDate(2015, 1, 20), 8.57)
        assertEquals(DayStatus.MISSED, h.statusOn(LocalDate(2015, 1, 20)), "just under, no rounding")
        h.withAmountOn(LocalDate(2015, 1, 21), 0.01)
        assertEquals(DayStatus.MET, h.statusOn(LocalDate(2015, 1, 20)), "reaches the scaled target")
    }

    @Test
    fun test_statusOn_skipScalesTheCap() {
        val h = buildMeasurableHabit(NumericalHabitType.AT_MOST, 4.0)
        h.frequency = Frequency.WEEKLY
        // A 4-coffee cap with one skip scales to about 3.4.
        h.withEntryOn(LocalDate(2015, 1, 19), Entry.SKIP)
        h.withAmountOn(LocalDate(2015, 1, 20), 4.0)
        assertEquals(DayStatus.MISSED, h.statusOn(LocalDate(2015, 1, 20)))
    }

    @Test
    fun test_statusOn_daysToComeCountAsDays() {
        setToday(LocalDate(2015, 1, 27))
        val h = buildMeasurableHabit(NumericalHabitType.AT_LEAST, 7.0)
        h.frequency = Frequency.WEEKLY
        // Week 2015-01-25..31: one skip and four days still to come leave a 6-day week.
        h.withEntryOn(LocalDate(2015, 1, 25), Entry.SKIP)
        h.withAmountOn(LocalDate(2015, 1, 26), 5.0)
        assertEquals(DayStatus.OPEN, h.todayStatus(), "5 of 6")
        h.withAmountToday(1.0)
        assertEquals(DayStatus.MET, h.todayStatus(), "6 of 6")
    }

    @Test
    fun test_statusOn_allSkippedPeriodHasNoVerdict() {
        val h = buildMeasurableHabit(NumericalHabitType.AT_LEAST, 10.0)
        h.frequency = Frequency.WEEKLY
        h.targetPeriodMode = TargetPeriodMode.ROLLING
        for (day in 19..25) h.withEntryOn(LocalDate(2015, 1, day), Entry.SKIP)
        assertNull(h.statusOn(LocalDate(2015, 1, 25)))
        assertEquals(DayStatus.MET, h.todayStatus(), "a skipped today is met, as on a daily habit")
        assertEquals(DayStatus.MISSED, h.statusOn(LocalDate(2015, 1, 18)), "one day counts")
    }

    @Test
    fun test_statusOn_daily_closedDays() {
        val yesterday = getToday().minus(1)
        val atLeast = buildMeasurableHabit(NumericalHabitType.AT_LEAST, 10.0)
        assertEquals(DayStatus.MISSED, atLeast.statusOn(yesterday), "at least, nothing entered")
        atLeast.withAmountOn(yesterday, 4.0)
        assertEquals(DayStatus.MISSED, atLeast.statusOn(yesterday), "at least, below the target")
        atLeast.withAmountOn(yesterday, 10.0)
        assertEquals(DayStatus.MET, atLeast.statusOn(yesterday), "at least, at the target")

        val atMost = buildMeasurableHabit(NumericalHabitType.AT_MOST, 5.0)
        atMost.withAmountOn(yesterday, 5.0)
        assertEquals(DayStatus.MET, atMost.statusOn(yesterday), "at most, at the cap")
        atMost.withAmountOn(yesterday, 6.0)
        assertEquals(DayStatus.MISSED, atMost.statusOn(yesterday), "at most, over the cap")
        atMost.withEntryOn(yesterday, Entry.SKIP)
        assertEquals(DayStatus.MET, atMost.statusOn(yesterday), "skip")
    }

    @Test
    fun test_statusOn_assumeNone() {
        val yesterday = getToday().minus(1)
        val daily = buildMeasurableHabit(NumericalHabitType.AT_MOST, 5.0)
        assertEquals(DayStatus.MET, daily.statusOn(yesterday), "on: nothing entered is met")
        assertEquals(DayStatus.OPEN, daily.todayStatus(), "on: today is still open")
        daily.assumeNone = false
        assertEquals(DayStatus.MISSED, daily.statusOn(yesterday), "off: nothing entered is missed")
        assertEquals(DayStatus.OPEN, daily.todayStatus(), "off: today is still open")

        val weekly = buildMeasurableHabit(NumericalHabitType.AT_MOST, 5.0)
        weekly.frequency = Frequency.WEEKLY
        weekly.assumeNone = false
        weekly.withAmountOn(LocalDate(2015, 1, 19), 5.0)
        assertEquals(DayStatus.MET, weekly.statusOn(yesterday), "off: empty days add 0 to the week")
    }

    @Test
    fun test_statusOn_yesOrNo_closedUnknownIsMissed() {
        val h = modelFactory.buildHabit()
        h.recompute()
        assertEquals(DayStatus.MISSED, h.statusOn(getToday().minus(1)), "closed")
        assertEquals(DayStatus.OPEN, h.statusOn(getToday()), "today")
    }

    @Test
    fun test_statusOn_rollingMonthIs30DaysWhenStoredAs31() {
        val h = buildMeasurableHabit(NumericalHabitType.AT_LEAST, 10.0)
        h.frequency = Frequency(1, 31)
        h.targetPeriodMode = TargetPeriodMode.ROLLING
        h.withAmountOn(LocalDate(2014, 12, 26), 10.0)
        assertEquals(DayStatus.MET, h.statusOn(LocalDate(2015, 1, 24)), "2014-12-26..2015-01-24")
        assertEquals(DayStatus.OPEN, h.todayStatus(), "2014-12-27..2015-01-25")
    }

    @Test
    fun test_statusOn_zeroTarget_nothingEnteredIsNotMet() {
        val h = buildMeasurableHabit(NumericalHabitType.AT_LEAST, 0.0)
        h.frequency = Frequency.WEEKLY
        assertEquals(DayStatus.OPEN, h.todayStatus(), "open week")
        assertEquals(DayStatus.MISSED, h.statusOn(LocalDate(2015, 1, 20)), "closed week")
        h.withAmountToday(0.0)
        assertEquals(DayStatus.MET, h.todayStatus(), "an amount of 0 reaches it")
    }

    @Test
    fun test_isEntered() {
        val h = modelFactory.buildHabit()
        assertFalse(h.isEnteredToday())
        h.originalEntries.add(Entry(getToday(), Entry.NO))
        h.recompute()
        assertTrue(h.isEnteredToday())
    }

    private fun buildMeasurableHabit(targetType: NumericalHabitType, target: Double): Habit {
        val h = modelFactory.buildHabit()
        h.type = HabitType.NUMERICAL
        h.targetType = targetType
        h.targetValue = target
        h.recompute()
        return h
    }

    private fun Habit.withAmountToday(amount: Double) {
        originalEntries.add(Entry(getToday(), Entry.AMOUNT, (amount * 1000).toInt()))
        recompute()
    }

    private fun Habit.withAmountOn(date: LocalDate, amount: Double) {
        originalEntries.add(Entry(date, Entry.AMOUNT, (amount * 1000).toInt()))
        recompute()
    }

    private fun Habit.withEntryOn(date: LocalDate, state: Int) {
        originalEntries.add(Entry(date, state))
        recompute()
    }

    private fun Habit.withEntryToday(state: Int) {
        originalEntries.add(Entry(getToday(), state))
        recompute()
    }

    @Test
    fun testURI() {
        assertTrue(habitList.isEmpty)
        val h = modelFactory.buildHabit()
        habitList.add(h)
        assertEquals(0L, h.id)
        assertEquals("content://org.isoron.uhabits/habit/0", h.uriString)
    }
}
