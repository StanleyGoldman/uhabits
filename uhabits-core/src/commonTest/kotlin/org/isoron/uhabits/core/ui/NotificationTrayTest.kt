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
package org.isoron.uhabits.core.ui

import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.BaseUnitTest
import org.isoron.uhabits.core.models.DayStatus
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Frequency
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitType
import org.isoron.uhabits.core.models.NumericalHabitType
import org.isoron.uhabits.core.models.Reminder
import org.isoron.uhabits.core.models.TargetPeriodMode
import org.isoron.uhabits.core.models.WeekdayList
import org.isoron.uhabits.core.preferences.MemoryStorage
import org.isoron.uhabits.core.preferences.Preferences
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationTrayTest : BaseUnitTest() {
    private lateinit var systemTray: FakeSystemTray
    private lateinit var tray: NotificationTray

    @BeforeTest
    override fun setUp() {
        super.setUp()
        systemTray = FakeSystemTray()
        tray = NotificationTray(taskRunner, commandRunner, Preferences(MemoryStorage()), systemTray)
    }

    @Test
    fun yesOrNo_firesWhileUnknown() {
        assertReminderShown(true, buildHabit())
    }

    @Test
    fun yesOrNo_silentAfterNo() {
        assertReminderShown(false, buildHabit().withEntryToday(Entry.NO))
    }

    @Test
    fun yesOrNo_silentOnYesAuto() {
        val habit = buildHabit()
        habit.frequency = Frequency.WEEKLY
        habit.originalEntries.add(Entry(getToday().minus(1), Entry.YES_MANUAL))
        habit.recompute()
        assertEquals(Entry.YES_AUTO, habit.computedEntries.get(getToday()).value, "setup")
        assertReminderShown(false, habit)
    }

    @Test
    fun atLeast_firesBelowTarget() {
        assertReminderShown(true, buildMeasurableHabit(NumericalHabitType.AT_LEAST, 10.0).withAmountToday(4.0))
    }

    @Test
    fun atLeast_silentAtTarget() {
        assertReminderShown(false, buildMeasurableHabit(NumericalHabitType.AT_LEAST, 10.0).withAmountToday(10.0))
    }

    @Test
    fun atMost_firesUnderCap() {
        assertReminderShown(true, buildMeasurableHabit(NumericalHabitType.AT_MOST, 5.0).withAmountToday(1.0))
    }

    @Test
    fun atMost_silentOverCap() {
        assertReminderShown(false, buildMeasurableHabit(NumericalHabitType.AT_MOST, 5.0).withAmountToday(6.0))
    }

    @Test
    fun weeklyAtLeast_silentOnceThePeriodIsMet() {
        val habit = buildWeeklyHabit(NumericalHabitType.AT_LEAST, 10.0).withAmountYesterday(10.0)
        assertReminderShown(false, habit)
    }

    @Test
    fun weeklyAtMost_silentOnceThePeriodIsMissed() {
        val habit = buildWeeklyHabit(NumericalHabitType.AT_MOST, 5.0).withAmountYesterday(6.0)
        assertReminderShown(false, habit)
    }

    @Test
    fun weeklyAtMost_firesWhileThePeriodIsOpen() {
        val habit = buildWeeklyHabit(NumericalHabitType.AT_MOST, 5.0).withAmountYesterday(4.0)
        assertReminderShown(true, habit)
    }

    @Test
    fun skipIfLogged_silentOnceAnAmountIsLogged() {
        val habit = buildMeasurableHabit(NumericalHabitType.AT_LEAST, 10.0)
        habit.skipReminderIfLogged = true
        assertReminderShown(false, habit.withAmountToday(4.0))
    }

    @Test
    fun skipIfLogged_silentOnceASkipIsLogged() {
        val habit = buildMeasurableHabit(NumericalHabitType.AT_LEAST, 10.0)
        habit.frequency = Frequency.WEEKLY
        habit.skipReminderIfLogged = true
        habit.withEntryToday(Entry.SKIP)
        assertEquals(DayStatus.OPEN, habit.todayStatus(), "setup")
        assertReminderShown(false, habit)
    }

    @Test
    fun skipIfLogged_firesWhenLoggedOnlyOnAnEarlierDay() {
        val habit = buildMeasurableHabit(NumericalHabitType.AT_LEAST, 10.0)
        habit.frequency = Frequency.WEEKLY
        habit.skipReminderIfLogged = true
        habit.originalEntries.add(Entry(getToday().minus(1), Entry.AMOUNT, 4000))
        habit.recompute()
        assertReminderShown(true, habit)
    }

    @Test
    fun skipIfLoggedOff_firesWhileOpenAfterLogging() {
        val habit = buildMeasurableHabit(NumericalHabitType.AT_LEAST, 10.0)
        habit.frequency = Frequency.WEEKLY
        assertEquals(false, habit.skipReminderIfLogged, "off by default")
        assertReminderShown(true, habit.withAmountToday(4.0))
    }

    private fun assertReminderShown(expected: Boolean, habit: Habit) {
        tray.show(habit, getToday(), 0)
        assertEquals(expected, systemTray.shown.contains(habit))
    }

    private fun buildHabit(): Habit {
        val habit = modelFactory.buildHabit()
        habit.reminder = Reminder(8, 30, WeekdayList.EVERY_DAY)
        habit.recompute()
        return habit
    }

    private fun buildMeasurableHabit(targetType: NumericalHabitType, target: Double): Habit {
        val habit = buildHabit()
        habit.type = HabitType.NUMERICAL
        habit.targetType = targetType
        habit.targetValue = target
        return habit
    }

    private fun buildWeeklyHabit(targetType: NumericalHabitType, target: Double): Habit {
        val habit = buildMeasurableHabit(targetType, target)
        habit.frequency = Frequency.WEEKLY
        habit.targetPeriodMode = TargetPeriodMode.ROLLING
        return habit
    }

    private fun Habit.withAmountYesterday(amount: Double): Habit {
        originalEntries.add(Entry(getToday().minus(1), Entry.AMOUNT, (amount * 1000).toInt()))
        recompute()
        return this
    }

    private fun Habit.withEntryToday(value: Int): Habit {
        originalEntries.add(Entry(getToday(), value))
        recompute()
        return this
    }

    private fun Habit.withAmountToday(amount: Double): Habit {
        originalEntries.add(Entry(getToday(), Entry.AMOUNT, (amount * 1000).toInt()))
        recompute()
        return this
    }

    private class FakeSystemTray : NotificationTray.SystemTray {
        val shown = mutableListOf<Habit>()
        override fun removeNotification(notificationId: Int) {}
        override fun showNotification(habit: Habit, notificationId: Int, date: LocalDate, reminderTime: Long) {
            shown.add(habit)
        }
        override fun log(msg: String) {}
    }
}
