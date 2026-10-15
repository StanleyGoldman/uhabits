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

import org.isoron.uhabits.core.BaseUnitTest
import org.isoron.uhabits.core.models.FrequencyLabel.EveryDay
import org.isoron.uhabits.core.models.FrequencyLabel.EveryMonth
import org.isoron.uhabits.core.models.FrequencyLabel.EveryWeek
import org.isoron.uhabits.core.models.FrequencyLabel.EveryXDays
import kotlin.test.Test
import kotlin.test.assertEquals

class FrequencyLabelTest : BaseUnitTest() {
    private fun measurable(den: Int, mode: TargetPeriodMode) =
        fixtures.createEmptyNumericalHabit(NumericalHabitType.AT_LEAST).apply {
            frequency = Frequency(1, den)
            targetPeriodMode = mode
        }

    @Test
    fun testRollingMeasurableHabitsReadAsEveryXDays() {
        assertEquals(EveryXDays(7), FrequencyLabel.of(measurable(7, TargetPeriodMode.ROLLING)))
        assertEquals(EveryXDays(30), FrequencyLabel.of(measurable(30, TargetPeriodMode.ROLLING)))
    }

    @Test
    fun testCalendarMeasurableHabitsReadAsEveryWeekOrMonth() {
        assertEquals(EveryWeek, FrequencyLabel.of(measurable(7, TargetPeriodMode.CALENDAR)))
        assertEquals(EveryMonth, FrequencyLabel.of(measurable(30, TargetPeriodMode.CALENDAR)))
    }

    @Test
    fun testDailyHabitIgnoresStoredRollingMode() {
        assertEquals(EveryDay, FrequencyLabel.of(measurable(1, TargetPeriodMode.ROLLING)))
    }

    @Test
    fun testYesOrNoHabitIgnoresStoredRollingMode() {
        val habit = fixtures.createEmptyHabit().apply {
            frequency = Frequency.WEEKLY
            targetPeriodMode = TargetPeriodMode.ROLLING
        }
        assertEquals(EveryWeek, FrequencyLabel.of(habit))
    }
}
