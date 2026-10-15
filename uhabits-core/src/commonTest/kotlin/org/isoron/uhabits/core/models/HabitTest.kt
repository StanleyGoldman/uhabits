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

import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.BaseUnitTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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
    fun todayStatus_yesOrNo() {
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
    fun todayStatus_yesOrNo_yesAutoIsMet() {
        val h = modelFactory.buildHabit()
        h.frequency = Frequency.WEEKLY
        h.originalEntries.add(Entry(getToday().minus(1), Entry.YES_MANUAL))
        h.recompute()
        assertEquals(Entry.YES_AUTO, h.computedEntries.get(getToday()).value, "setup")
        assertEquals(DayStatus.MET, h.todayStatus())
    }

    @Test
    fun todayStatus_atLeast() {
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
    fun todayStatus_atMost() {
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
    fun todayStatus_measurableSkipIsMet() {
        val atLeast = buildMeasurableHabit(NumericalHabitType.AT_LEAST, 10.0)
        atLeast.withEntryToday(Entry.SKIP)
        assertEquals(DayStatus.MET, atLeast.todayStatus(), "at least")
        val atMost = buildMeasurableHabit(NumericalHabitType.AT_MOST, 5.0)
        atMost.withEntryToday(Entry.SKIP)
        assertEquals(DayStatus.MET, atMost.todayStatus(), "at most")
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

    private fun Habit.withEntryToday(value: Int) {
        originalEntries.add(Entry(getToday(), value))
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
