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

import org.isoron.uhabits.core.models.MeasurableFrequencyOption.EVERY_30_DAYS
import org.isoron.uhabits.core.models.MeasurableFrequencyOption.EVERY_7_DAYS
import org.isoron.uhabits.core.models.MeasurableFrequencyOption.EVERY_DAY
import org.isoron.uhabits.core.models.MeasurableFrequencyOption.EVERY_MONTH
import org.isoron.uhabits.core.models.MeasurableFrequencyOption.EVERY_WEEK
import org.isoron.uhabits.core.models.TargetPeriodMode.CALENDAR
import org.isoron.uhabits.core.models.TargetPeriodMode.ROLLING
import kotlin.test.Test
import kotlin.test.assertEquals

class MeasurableFrequencyOptionTest {
    @Test
    fun testMenuOffersFiveEntriesInOrder() {
        assertEquals(
            listOf(EVERY_DAY, EVERY_WEEK, EVERY_7_DAYS, EVERY_MONTH, EVERY_30_DAYS),
            MeasurableFrequencyOption.entries
        )
    }

    @Test
    fun testPickingAnEntrySetsFrequencyAndMode() {
        assertEquals(Frequency(1, 7), EVERY_WEEK.frequency)
        assertEquals(CALENDAR, EVERY_WEEK.targetPeriodMode(stored = ROLLING))
        assertEquals(Frequency(1, 7), EVERY_7_DAYS.frequency)
        assertEquals(ROLLING, EVERY_7_DAYS.targetPeriodMode(stored = CALENDAR))
        assertEquals(Frequency(1, 30), EVERY_MONTH.frequency)
        assertEquals(CALENDAR, EVERY_MONTH.targetPeriodMode(stored = ROLLING))
        assertEquals(Frequency(1, 30), EVERY_30_DAYS.frequency)
        assertEquals(ROLLING, EVERY_30_DAYS.targetPeriodMode(stored = CALENDAR))
    }

    @Test
    fun testPickingEveryDayKeepsStoredMode() {
        assertEquals(Frequency.DAILY, EVERY_DAY.frequency)
        assertEquals(ROLLING, EVERY_DAY.targetPeriodMode(stored = ROLLING))
        assertEquals(CALENDAR, EVERY_DAY.targetPeriodMode(stored = CALENDAR))
    }

    @Test
    fun testEntriesReadAsTheirLabels() {
        assertEquals(FrequencyLabel.EveryDay, EVERY_DAY.label)
        assertEquals(FrequencyLabel.EveryWeek, EVERY_WEEK.label)
        assertEquals(FrequencyLabel.EveryXDays(7), EVERY_7_DAYS.label)
        assertEquals(FrequencyLabel.EveryMonth, EVERY_MONTH.label)
        assertEquals(FrequencyLabel.EveryXDays(30), EVERY_30_DAYS.label)
    }
}
