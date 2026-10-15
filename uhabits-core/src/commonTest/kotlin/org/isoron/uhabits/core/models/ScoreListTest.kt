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
import org.isoron.uhabits.core.models.Entry.Companion.SKIP
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

open class BaseScoreListTest : BaseUnitTest() {
    protected lateinit var habit: Habit
    protected lateinit var today: LocalDate

    @BeforeTest
    override fun setUp() {
        super.setUp()
        today = getToday()
    }

    protected fun checkScoreValues(expectedValues: DoubleArray) {
        var current = today
        val scores = habit.scores
        for (expectedValue in expectedValues) {
            assertCloseTo(expectedValue, scores[current].value, E)
            current = current.minus(1)
        }
    }

    companion object {
        const val E = 1e-6

        fun assertCloseTo(expected: Double, actual: Double, epsilon: Double) {
            val diff = kotlin.math.abs(expected - actual)
            assertTrue(diff <= epsilon, "Expected $expected ± $epsilon, but got $actual (diff=$diff)")
        }
    }
}

class YesNoScoreListTest : BaseScoreListTest() {
    @BeforeTest
    override fun setUp() {
        super.setUp()
        habit = fixtures.createEmptyHabit()
    }

    @Test
    fun test_getValue() {
        check(0, 20)
        val expectedValues = doubleArrayOf(
            0.655747,
            0.636894,
            0.617008,
            0.596033,
            0.573910,
            0.550574,
            0.525961,
            0.500000,
            0.472617,
            0.443734,
            0.413270,
            0.381137,
            0.347244,
            0.311495,
            0.273788,
            0.234017,
            0.192067,
            0.147820,
            0.101149,
            0.051922,
            0.000000,
            0.000000,
            0.000000
        )
        checkScoreValues(expectedValues)
    }

    @Test
    fun test_getValueWithSkip() {
        check(0, 20)
        addSkip(5)
        addSkip(10)
        addSkip(11)
        habit.recompute()
        val expectedValues = doubleArrayOf(
            0.596033,
            0.573910,
            0.550574,
            0.525961,
            0.500000,
            0.472617,
            0.472617,
            0.443734,
            0.413270,
            0.381137,
            0.347244,
            0.347244,
            0.347244,
            0.311495,
            0.273788,
            0.234017,
            0.192067,
            0.147820,
            0.101149,
            0.051922,
            0.000000,
            0.000000,
            0.000000
        )
        checkScoreValues(expectedValues)
    }

    @Test
    fun test_getValueWithSkip2() {
        check(5)
        addSkip(4)
        habit.recompute()
        val expectedValues = doubleArrayOf(
            0.041949,
            0.044247,
            0.046670,
            0.049226,
            0.051922,
            0.051922,
            0.0
        )
        checkScoreValues(expectedValues)
    }

    @Test
    fun test_imperfectNonDaily() {
        // If the habit should be performed 3 times per week and the user misses 1 repetition
        // each week, score should converge to 66%.
        habit.frequency = Frequency(3, 7)
        val values = mutableListOf<Int>()
        for (k in 0..99) {
            values.add(Entry.YES_MANUAL)
            values.add(Entry.YES_MANUAL)
            values.add(Entry.NO)
            values.add(Entry.NO)
            values.add(Entry.NO)
            values.add(Entry.NO)
            values.add(Entry.NO)
        }
        check(values)
        assertCloseTo(2 / 3.0, habit.scores[today].value, E)

        // Missing 2 repetitions out of 4 per week, the score should converge to 50%
        habit.frequency = Frequency(4, 7)
        habit.recompute()
        assertCloseTo(0.5, habit.scores[today].value, E)
    }

    @Test
    fun test_irregularNonDaily() {
        // If the user performs habit perfectly each week, but on different weekdays,
        // score should still converge to 100%
        habit.frequency = Frequency(1, 7)
        val values = mutableListOf<Int>()
        for (k in 0..99) {
            // Week 0
            values.add(Entry.YES_MANUAL)
            values.add(Entry.NO)
            values.add(Entry.NO)
            values.add(Entry.NO)
            values.add(Entry.NO)
            values.add(Entry.NO)
            values.add(Entry.NO)

            // Week 1
            values.add(Entry.NO)
            values.add(Entry.NO)
            values.add(Entry.NO)
            values.add(Entry.NO)
            values.add(Entry.NO)
            values.add(Entry.NO)
            values.add(Entry.YES_MANUAL)
        }
        check(values)
        assertCloseTo(1.0, habit.scores[today].value, 1e-3)
    }

    @Test
    fun skipsShouldNotLowerLaterScores() {
        // A "6 times per week" habit with 4 skipped days: the days after the skips should
        // not drop below the score the habit had before them
        habit.frequency = Frequency(6, 7)
        for (i in 10..13) addSkip(i)
        for (i in 0..499) if (i !in 10..13) check(i)
        habit.recompute()
        val before = habit.scores[today.minus(14)].value
        for (i in 0..13) assertTrue(habit.scores[today.minus(i)].value >= before - E)
    }

    @Test
    fun shouldAchieveHighScoreInReasonableTime() {
        // Daily habits should achieve at least 99% in 3 months
        habit = fixtures.createEmptyHabit()
        habit.frequency = Frequency.DAILY
        for (i in 0..89) check(i)
        habit.recompute()
        assertTrue(habit.scores[today].value > 0.99)

        // Weekly habits should achieve at least 99% in 9 months
        habit = fixtures.createEmptyHabit()
        habit.frequency = Frequency.WEEKLY
        for (i in 0..38) check(7 * i)
        habit.recompute()
        assertTrue(habit.scores[today].value > 0.99)

        // Monthly habits should achieve at least 99% in 18 months
        habit.frequency = Frequency(1, 30)
        for (i in 0..17) check(30 * i)
        habit.recompute()
        assertTrue(habit.scores[today].value > 0.99)
    }

    @Test
    fun test_recompute() {
        assertCloseTo(0.0, habit.scores[today].value, E)
        check(0, 2)
        assertCloseTo(0.101149, habit.scores[today].value, E)
        habit.frequency = Frequency(1, 2)
        habit.recompute()
        assertCloseTo(0.054816, habit.scores[today].value, E)
    }

    @Test
    fun test_addThenRemove() {
        val habit = fixtures.createEmptyHabit()
        habit.recompute()
        assertCloseTo(0.0, habit.scores[today].value, E)
        habit.originalEntries.add(Entry(today, Entry.YES_MANUAL))
        habit.recompute()
        assertCloseTo(0.051922, habit.scores[today].value, E)
        habit.originalEntries.add(Entry(today, Entry.UNKNOWN))
        habit.recompute()
        assertCloseTo(0.0, habit.scores[today].value, E)
    }

    private fun check(offset: Int) {
        val entries = habit.originalEntries
        entries.add(Entry(today.minus(offset), Entry.YES_MANUAL))
    }

    private fun check(from: Int, to: Int) {
        val entries = habit.originalEntries
        for (i in from until to) entries.add(Entry(today.minus(i), Entry.YES_MANUAL))
        habit.recompute()
    }

    private fun check(values: MutableList<Int>) {
        val entries = habit.originalEntries
        for (i in values.indices) if (values[i] == Entry.YES_MANUAL) {
            entries.add(
                Entry(
                    today.minus(i),
                    Entry.YES_MANUAL
                )
            )
        }
        habit.recompute()
    }

    private fun addSkip(day: Int) {
        val entries = habit.originalEntries
        entries.add(Entry(today.minus(day), Entry.SKIP))
    }
}

open class NumericalScoreListTest : BaseScoreListTest() {
    protected fun addAmount(day: Int, amount: Int) {
        habit.originalEntries.add(Entry(today.minus(day), Entry.AMOUNT, amount))
    }

    protected fun addAmounts(from: Int, to: Int, amount: Int) {
        for (i in from until to) addAmount(i, amount)
        habit.recompute()
    }

    protected fun addSkips(from: Int, to: Int) {
        for (i in from until to) habit.originalEntries.add(Entry(today.minus(i), Entry.SKIP))
        habit.recompute()
    }
}

class NumericalAtLeastScoreListTest : NumericalScoreListTest() {
    @BeforeTest
    override fun setUp() {
        super.setUp()
        habit = fixtures.createEmptyNumericalHabit(NumericalHabitType.AT_LEAST)
    }

    @Test
    fun test_withZeroTarget() {
        habit = fixtures.createNumericalHabit()
        habit.targetValue = 0.0
        habit.recompute()
        assertTrue(habit.scores[today].value.isFinite())
    }

    @Test
    fun test_getValue() {
        addAmounts(0, 20, 2000)
        val expectedValues = doubleArrayOf(
            0.655747,
            0.636894,
            0.617008,
            0.596033,
            0.573910,
            0.550574,
            0.525961,
            0.500000,
            0.472617,
            0.443734,
            0.413270,
            0.381137,
            0.347244,
            0.311495,
            0.273788,
            0.234017,
            0.192067,
            0.147820,
            0.101149,
            0.051922,
            0.000000,
            0.000000,
            0.000000
        )
        checkScoreValues(expectedValues)
    }

    @Test
    fun test_recompute() {
        assertCloseTo(0.0, habit.scores[today].value, E)
        addAmounts(0, 2, 2000)
        assertCloseTo(0.101149, habit.scores[today].value, E)
        habit.frequency = Frequency(1, 2)
        habit.recompute()
        assertCloseTo(0.054816, habit.scores[today].value, E)
    }

    @Test
    fun shouldAchieveHighScoreInReasonableTime() {
        // Daily habits should achieve at least 99% in 3 months
        habit = fixtures.createEmptyNumericalHabit(NumericalHabitType.AT_LEAST)
        habit.frequency = Frequency.DAILY
        for (i in 0..89) addAmount(i, 2000)
        habit.recompute()
        assertTrue(habit.scores[today].value > 0.99)

        // Weekly habits should achieve at least 99% in 9 months
        habit = fixtures.createEmptyNumericalHabit(NumericalHabitType.AT_LEAST)
        habit.frequency = Frequency.WEEKLY
        for (i in 0..38) addAmount(7 * i, 2000)
        habit.recompute()
        assertTrue(habit.scores[today].value > 0.99)

        // Monthly habits should achieve at least 99% in 18 months
        habit.frequency = Frequency(1, 30)
        for (i in 0..17) addAmount(30 * i, 2000)
        habit.recompute()
        assertTrue(habit.scores[today].value > 0.99)
    }

    @Test
    fun shouldAchieveComparableScoreToProgress() {
        addAmounts(0, 500, 1000)
        assertCloseTo(0.5, habit.scores[today].value, E)

        addAmounts(0, 500, 500)
        assertCloseTo(0.25, habit.scores[today].value, E)
    }

    @Test
    fun test_irregularNonDaily() {
        // If the user meets the weekly target each week, but on different weekdays,
        // score should still converge to 100%
        habit.frequency = Frequency.WEEKLY
        for (k in 0..99) {
            addAmount(14 * k, 2000)
            addAmount(14 * k + 13, 2000)
        }
        habit.recompute()
        assertCloseTo(1.0, habit.scores[today].value, 1e-3)
    }

    @Test
    fun overeachievingIsntRelevant() {
        addAmount(0, 10000000)
        habit.recompute()
        assertCloseTo(0.051922, habit.scores[today].value, E)
    }
}

class NumericalAtLeastScoreListWithSkipTest : NumericalScoreListTest() {
    @BeforeTest
    override fun setUp() {
        super.setUp()
        habit = fixtures.createEmptyNumericalHabit(NumericalHabitType.AT_LEAST)
    }

    @Test
    fun test_getValue() {
        addAmounts(0, 10, 2000)
        addSkips(10, 11)
        addAmounts(11, 15, 2000)
        addSkips(15, 16)
        addAmounts(16, 20, 2000)
        val expectedValues = doubleArrayOf(
            0.617008,
            0.596033,
            0.573910,
            0.550574,
            0.525961,
            0.500000,
            0.472617,
            0.443734,
            0.413270,
            0.381137,
            0.347244, // skipped day should have the same score as the previous day
            0.347244,
            0.311495,
            0.273788,
            0.234017,
            0.192067, // skipped day should have the same score as the previous day
            0.192067,
            0.147820,
            0.101149,
            0.051922,
            0.000000,
            0.000000,
            0.000000
        )
        checkScoreValues(expectedValues)
    }

    @Test
    fun skipsShouldNotAffectScore() {
        addAmounts(0, 500, 1000)
        val initialScore = habit.scores[today].value

        addSkips(500, 1000)
        assertCloseTo(initialScore, habit.scores[today].value, E)

        addAmounts(0, 300, 1000)
        addSkips(300, 500)
        addAmounts(500, 700, 1000)

        // skipped days should be treated as if they never existed
        assertCloseTo(initialScore, habit.scores[today].value, E)
    }

    @Test
    fun skipsShouldNotLowerLaterScoresOfWeeklyHabits() {
        // "At least 7 miles per week", one mile every day except 4 skipped days: the days
        // after the skips should not drop below the score the habit had before them
        habit.frequency = Frequency.WEEKLY
        habit.targetValue = 7.0
        addSkips(10, 14)
        addAmounts(14, 500, 1000)
        addAmounts(0, 10, 1000)
        val before = habit.scores[today.minus(14)].value
        for (i in 0..13) assertTrue(habit.scores[today.minus(i)].value >= before - E)
    }
}

class NumericalAtMostScoreListTest : NumericalScoreListTest() {
    @BeforeTest
    override fun setUp() {
        super.setUp()
        habit = fixtures.createEmptyNumericalHabit(NumericalHabitType.AT_MOST)
    }

    @Test
    fun test_withZeroTarget() {
        habit = fixtures.createNumericalHabit()
        habit.targetType = NumericalHabitType.AT_MOST
        habit.targetValue = 0.0
        habit.recompute()
        assertTrue(habit.scores[today].value.isFinite())
    }

    @Test
    fun test_getValue() {
        addAmount(20, 1000)
        addAmounts(0, 20, 5000)
        val expectedValues = doubleArrayOf(
            0.344253,
            0.363106,
            0.382992,
            0.403967,
            0.426090,
            0.449426,
            0.474039,
            0.500000,
            0.527383,
            0.556266,
            0.586730,
            0.618863,
            0.652756,
            0.688505,
            0.726212,
            0.765983,
            0.807933,
            0.852180,
            0.898851,
            0.948078,
            1.0,
            0.0,
            0.0
        )
        checkScoreValues(expectedValues)
    }

    @Test
    fun test_recompute() {
        habit.recompute()
        assertCloseTo(1.0, habit.scores[today].value, E)
        addAmounts(0, 2, 5000)
        assertCloseTo(0.898850, habit.scores[today].value, E)
        habit.frequency = Frequency(1, 2)
        habit.recompute()
        assertCloseTo(0.954092, habit.scores[today].value, E)
    }

    @Test
    fun shouldAchieveComparableScoreToProgress() {
        addAmounts(0, 500, 3000)
        assertCloseTo(0.5, habit.scores[today].value, E)

        addAmounts(0, 500, 3500)
        assertCloseTo(0.25, habit.scores[today].value, E)
    }

    @Test
    fun test_irregularNonDaily() {
        // If the user stays within the weekly cap each week, but on different weekdays,
        // score should stay at 100%
        habit.frequency = Frequency.WEEKLY
        for (k in 0..99) {
            addAmount(14 * k, 2000)
            addAmount(14 * k + 13, 2000)
        }
        habit.recompute()
        assertCloseTo(1.0, habit.scores[today].value, 1e-3)
    }

    @Test
    fun withoutAssumeNoneScoreShouldStartAtZero() {
        habit.assumeNone = false
        addAmounts(0, 1, 0)
        assertCloseTo(0.051922, habit.scores[today].value, E)
    }

    @Test
    fun withoutAssumeNoneClosedDaysWithNothingEnteredShouldGradeZero() {
        // Within the cap for 498 days, then nothing entered yesterday or today: yesterday
        // grades 0, and today is still open, so it leaves the score unchanged
        habit.assumeNone = false
        addAmounts(2, 500, 0)
        assertCloseTo(1.0, habit.scores[today.minus(2)].value, E)
        assertCloseTo(0.948078, habit.scores[today.minus(1)].value, E)
        assertCloseTo(0.948078, habit.scores[today].value, E)
    }

    @Test
    fun withAssumeNoneDaysWithNothingEnteredShouldBeMet() {
        addAmounts(2, 500, 0)
        assertCloseTo(1.0, habit.scores[today.minus(1)].value, E)
        assertCloseTo(1.0, habit.scores[today].value, E)
    }

    @Test
    fun skipsShouldLowerTheCap() {
        // "At most 2 per week" grades on 14 days against 4. With one skip in the window the
        // cap scales to 4 × 13/14 ≈ 3.7, so 4 goes over it
        habit.frequency = Frequency.WEEKLY
        addSkips(1, 2)
        addAmounts(0, 1, 4000)
        assertTrue(habit.scores[today].value < 1.0)
    }

    @Test
    fun skipsShouldAddNothingToTheWindow() {
        // "At most 0 per week", nothing over the cap and one skipped day: the score
        // should stay at 100%
        habit.frequency = Frequency.WEEKLY
        habit.targetValue = 0.0
        addAmounts(0, 100, 0)
        addSkips(5, 6)
        assertCloseTo(1.0, habit.scores[today].value, E)
    }

    @Test
    fun undereachievingIsntRelevant() {
        addAmount(1, 10000000)
        habit.recompute()
        assertCloseTo(0.950773, habit.scores[today].value, E)
    }

    @Test
    fun overeachievingIsntRelevant() {
        addAmount(0, 5000)

        addAmount(1, 0)
        habit.recompute()
        assertCloseTo(0.948077, habit.scores[today].value, E)

        addAmount(1, 1000)
        habit.recompute()
        assertCloseTo(0.948077, habit.scores[today].value, E)
    }
}
