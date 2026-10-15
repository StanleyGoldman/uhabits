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
package org.isoron.uhabits.core.ui.widgets

import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.resetCalls
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.BaseUnitTest
import org.isoron.uhabits.core.commands.CommandRunner
import org.isoron.uhabits.core.commands.CreateRepetitionCommand
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Entry.Companion.AMOUNT
import org.isoron.uhabits.core.models.Entry.Companion.SKIP
import org.isoron.uhabits.core.models.Entry.Companion.UNKNOWN
import org.isoron.uhabits.core.models.Entry.Companion.nextToggleValue
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.preferences.Preferences
import org.isoron.uhabits.core.ui.NotificationTray
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import dev.mokkery.verify.VerifyMode.Companion.not as notCalled

class WidgetBehaviorTest : BaseUnitTest() {
    private lateinit var notificationTray: NotificationTray
    private lateinit var preferences: Preferences
    private lateinit var behavior: WidgetBehavior
    private lateinit var automation: WidgetBehavior
    private lateinit var habit: Habit
    private lateinit var today: LocalDate
    private val oneUnit = 1000

    @BeforeTest
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        habit = fixtures.createEmptyHabit()
        commandRunner = mock()
        notificationTray = mock()
        preferences = mock()
        behavior = WidgetBehavior(habitList, commandRunner, notificationTray, preferences)
        automation = WidgetBehavior(habitList, CommandRunner(taskRunner), notificationTray, preferences)
        today = getToday()
    }

    @Test
    fun testOnAddRepetition() {
        behavior.onAddRepetition(habit, today)
        verify {
            commandRunner.run(
                CreateRepetitionCommand(habitList, habit, today, Entry.YES_MANUAL, amount = null, notes = "")
            )
        }
        verify { notificationTray.cancel(habit) }
        verify(notCalled) { preferences.isSkipEnabled }
    }

    @Test
    fun testOnRemoveRepetition() {
        behavior.onRemoveRepetition(habit, today)
        verify {
            commandRunner.run(
                CreateRepetitionCommand(habitList, habit, today, Entry.NO, amount = null, notes = "")
            )
        }
        verify { notificationTray.cancel(habit) }
        verify(notCalled) { preferences.isSkipEnabled }
    }

    @Test
    fun testOnToggleRepetition() {
        for (skipEnabled in listOf(true, false)) for (
        currentValue in listOf(
            Entry.NO,
            Entry.YES_MANUAL,
            Entry.YES_AUTO,
            Entry.SKIP
        )
        ) {
            every { preferences.isSkipEnabled } returns skipEnabled
            val nextValue: Int = nextToggleValue(
                currentValue,
                isSkipEnabled = skipEnabled,
                areQuestionMarksEnabled = false
            )
            habit.originalEntries.add(Entry(today, currentValue))
            behavior.onToggleRepetition(habit, today)
            verify { preferences.isSkipEnabled }
            verify {
                commandRunner.run(
                    CreateRepetitionCommand(habitList, habit, today, nextValue, amount = null, notes = "")
                )
            }
            verify {
                notificationTray.cancel(
                    habit
                )
            }
            resetCalls(preferences, commandRunner, notificationTray)
        }
    }

    @Test
    fun testIncrementOnUnknownDayRecordsOneUnit() {
        val day = today.minus(2)
        val measurable = fixtures.createNumericalHabit()
        automation.onIncrement(measurable, day, oneUnit)
        assertEquals(Entry(day, AMOUNT, oneUnit), measurable.originalEntries.get(day))
    }

    @Test
    fun testIncrementAddsToAmount() {
        val measurable = fixtures.createNumericalHabit()
        measurable.originalEntries.add(Entry(today, AMOUNT, 500, "notes"))
        measurable.recompute()
        automation.onIncrement(measurable, today, oneUnit)
        assertEquals(Entry(today, AMOUNT, 1500, "notes"), measurable.originalEntries.get(today))
        verify { notificationTray.cancel(measurable) }
    }

    @Test
    fun testIncrementOnSkippedDayReplacesSkipAndKeepsNotes() {
        val measurable = fixtures.createNumericalHabit()
        measurable.originalEntries.add(Entry(today, SKIP, null, "sick"))
        measurable.recompute()
        automation.onIncrement(measurable, today, oneUnit)
        assertEquals(Entry(today, AMOUNT, oneUnit, "sick"), measurable.originalEntries.get(today))
    }

    @Test
    fun testDecrementOnUnknownOrSkippedDayDoesNothing() {
        val unknownDay = today.minus(2)
        val measurable = fixtures.createNumericalHabit()
        measurable.originalEntries.add(Entry(today, SKIP, null, "sick"))
        measurable.recompute()
        automation.onDecrement(measurable, unknownDay, oneUnit)
        automation.onDecrement(measurable, today, oneUnit)
        assertEquals(Entry(unknownDay, UNKNOWN), measurable.originalEntries.get(unknownDay))
        assertEquals(Entry(today, SKIP, null, "sick"), measurable.originalEntries.get(today))
        verify(exactly(2)) { notificationTray.cancel(measurable) }
    }

    @Test
    fun testDecrementSubtractsAndStopsAtZero() {
        val yesterday = today.minus(1)
        val measurable = fixtures.createNumericalHabit()
        measurable.originalEntries.add(Entry(today, AMOUNT, 2500, "notes"))
        measurable.originalEntries.add(Entry(yesterday, AMOUNT, 500))
        measurable.recompute()
        automation.onDecrement(measurable, today, oneUnit)
        automation.onDecrement(measurable, yesterday, oneUnit)
        assertEquals(Entry(today, AMOUNT, 1500, "notes"), measurable.originalEntries.get(today))
        assertEquals(Entry(yesterday, AMOUNT, 0), measurable.originalEntries.get(yesterday))
        verify { notificationTray.cancel(measurable) }
    }

    @Test
    fun testIncrementAndDecrementLeaveYesNoHabitAlone() {
        val yesNo = fixtures.createShortHabit()
        val before = yesNo.originalEntries.get(today)
        automation.onIncrement(yesNo, today, oneUnit)
        automation.onDecrement(yesNo, today, oneUnit)
        assertEquals(before, yesNo.originalEntries.get(today))
    }
}
