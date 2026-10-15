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
import org.isoron.uhabits.core.BaseUnitTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HabitMatcherTest : BaseUnitTest() {

    private fun buildHabit(
        name: String,
        question: String = "",
        description: String = ""
    ): Habit {
        val habit = modelFactory.buildHabit()
        habit.name = name
        habit.question = question
        habit.description = description
        return habit
    }

    @Test
    fun testSearch() {
        val yogaPractice = buildHabit("Yoga practice")
        val running = buildHabit("Running", question = "Did you run today?", description = "daily jog")
        val exercise = buildHabit("Exercise", question = "Did you do yoga today?")
        val mediter = buildHabit("Méditer", description = "mindfulness session")
        val stretching = buildHabit("🧘 Stretching")
        val run10k = buildHabit("10k Run")

        val habits = listOf(yogaPractice, running, exercise, mediter, stretching, run10k)

        // Match by name
        assertMatches(habits, "yoga", listOf(yogaPractice, exercise))

        // Match by question only
        assertMatches(habits, "run", listOf(running, run10k))

        // Match by description only
        assertMatches(habits, "jog", listOf(running))

        // Case-insensitive
        assertMatches(habits, "YOGA", listOf(yogaPractice, exercise))

        // Leading/trailing whitespace
        assertMatches(habits, "  yoga  ", listOf(yogaPractice, exercise))

        // Empty query matches all
        assertMatches(habits, "", listOf(yogaPractice, running, exercise, mediter, stretching, run10k))

        // Whitespace-only query matches all
        assertMatches(habits, "   ", listOf(yogaPractice, running, exercise, mediter, stretching, run10k))

        // Accented character
        assertMatches(habits, "méditer", listOf(mediter))

        // Case-insensitive accented
        assertMatches(habits, "MÉDITER", listOf(mediter))

        // Unaccented query does NOT match accented habit
        assertMatches(habits, "mediter", emptyList())

        // Emoji in habit name doesn't block text match
        assertMatches(habits, "stretching", listOf(stretching))

        // Numbers in name
        assertMatches(habits, "10k", listOf(run10k))

        // Single character
        assertMatches(habits, "y", listOf(yogaPractice, running, exercise))

        // Query longer than all fields
        assertMatches(habits, "a".repeat(100), emptyList())

        // No match
        assertMatches(habits, "swimming", emptyList())
    }

    @Test
    fun testHideEntered_keepsHabitsWithNothingRecordedToday() {
        val untouched = buildHabit("Untouched")
        val unknown = buildHabit("Unknown").withEntryToday(Entry.UNKNOWN)
        val autoOnly = buildHabit("Auto only").apply {
            frequency = Frequency(3, 7)
            for (daysAgo in 1..3) originalEntries.add(Entry(getToday().minus(daysAgo), Entry.YES_MANUAL))
            recompute()
        }
        assertEquals(Entry.YES_AUTO, autoOnly.computedEntries.get(getToday()).state)
        val measurableUntouched = buildMeasurableHabit("Measurable untouched")
        val measurableUnknown = buildMeasurableHabit("Measurable unknown").withEntryToday(Entry.UNKNOWN)

        val habits = listOf(untouched, unknown, autoOnly, measurableUntouched, measurableUnknown)
        assertEquals(habits, habits.filter(HabitMatcher(isEnteredAllowed = false)::matches))
    }

    @Test
    fun testHideEntered_hidesHabitsWithSomethingRecordedToday() {
        val yes = buildHabit("Yes").withEntryToday(Entry.YES_MANUAL)
        val no = buildHabit("No").withEntryToday(Entry.NO)
        val skip = buildHabit("Skip").withEntryToday(Entry.SKIP)
        val amount = buildMeasurableHabit("Amount").withAmountToday(5000)
        val zero = buildMeasurableHabit("Zero").withAmountToday(0)
        val tinyAmount = buildMeasurableHabit("0.001").withAmountToday(1)
        val measurableSkip = buildMeasurableHabit("Measurable skip").withEntryToday(Entry.SKIP)

        val habits = listOf(yes, no, skip, amount, zero, tinyAmount, measurableSkip)
        assertEquals(emptyList(), habits.filter(HabitMatcher(isEnteredAllowed = false)::matches))
    }

    @Test
    fun testHideCompleted_hidesHabitsMetToday() {
        val yes = buildHabit("Yes").withEntryToday(Entry.YES_MANUAL)
        val skip = buildHabit("Skip").withEntryToday(Entry.SKIP)
        val atTarget = buildMeasurableHabit("At target").withAmountToday(10000)
        val measurableSkip = buildMeasurableHabit("Measurable skip").withEntryToday(Entry.SKIP)

        val habits = listOf(yes, skip, atTarget, measurableSkip)
        assertEquals(emptyList(), habits.filter(HabitMatcher(isCompletedAllowed = false)::matches))
    }

    @Test
    fun testHideCompleted_keepsHabitsOpenOrMissedToday() {
        val untouched = buildHabit("Untouched")
        val no = buildHabit("No").withEntryToday(Entry.NO)
        val belowTarget = buildMeasurableHabit("Below target").withAmountToday(4000)
        val underCap = buildMeasurableHabit("Under cap", NumericalHabitType.AT_MOST, 5.0)
            .withAmountToday(1000)
        val overCap = buildMeasurableHabit("Over cap", NumericalHabitType.AT_MOST, 5.0)
            .withAmountToday(6000)

        val habits = listOf(untouched, no, belowTarget, underCap, overCap)
        assertEquals(habits, habits.filter(HabitMatcher(isCompletedAllowed = false)::matches))
    }

    @Test
    fun testHideCompleted_followsThePeriodVerdict() {
        // Today is Sunday 2015-01-25, the last day of a Monday-first week.
        setFirstWeekday { DayOfWeek.MONDAY }
        val weekMet = buildMeasurableHabit("Week met").weekly().withAmountOn(getToday().minus(3), 10000)
        val weekOpen = buildMeasurableHabit("Week open").weekly().withAmountOn(getToday().minus(3), 4000)
        val capOpen = buildMeasurableHabit("Cap open", NumericalHabitType.AT_MOST, 5.0)
            .weekly().withAmountOn(getToday().minus(3), 5000)
        val capOver = buildMeasurableHabit("Cap over", NumericalHabitType.AT_MOST, 5.0)
            .weekly().withAmountOn(getToday().minus(3), 6000)

        val habits = listOf(weekMet, weekOpen, capOpen, capOver)
        assertEquals(
            listOf(weekOpen, capOpen, capOver),
            habits.filter(HabitMatcher(isCompletedAllowed = false)::matches)
        )
    }

    private fun Habit.weekly(): Habit {
        frequency = Frequency.WEEKLY
        return this
    }

    private fun Habit.withAmountOn(date: LocalDate, amount: Int): Habit {
        originalEntries.add(Entry(date, Entry.AMOUNT, amount))
        recompute()
        return this
    }

    private fun buildMeasurableHabit(
        name: String,
        targetType: NumericalHabitType = NumericalHabitType.AT_LEAST,
        targetValue: Double = 10.0
    ): Habit {
        return buildHabit(name).apply {
            type = HabitType.NUMERICAL
            this.targetType = targetType
            this.targetValue = targetValue
        }
    }

    private fun Habit.withEntryToday(state: Int): Habit {
        originalEntries.add(Entry(getToday(), state))
        recompute()
        return this
    }

    private fun Habit.withAmountToday(amount: Int): Habit {
        originalEntries.add(Entry(getToday(), Entry.AMOUNT, amount))
        recompute()
        return this
    }

    private fun assertMatches(habits: List<Habit>, query: String, expected: List<Habit>) {
        val matcher = HabitMatcher(searchQuery = query)
        val actual = habits.filter(matcher::matches)
        assertEquals(expected.toSet(), actual.toSet(), "query '$query'")
    }
}
