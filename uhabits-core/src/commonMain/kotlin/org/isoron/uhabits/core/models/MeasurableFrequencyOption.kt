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

/**
 * An entry of a measurable habit's Frequency menu. Picking one sets both the
 * frequency and the target period mode; "Every day" keeps the stored mode.
 */
enum class MeasurableFrequencyOption(
    private val denominator: Int,
    private val mode: TargetPeriodMode?
) {
    EVERY_DAY(1, null),
    EVERY_WEEK(7, TargetPeriodMode.CALENDAR),
    EVERY_7_DAYS(7, TargetPeriodMode.ROLLING),
    EVERY_MONTH(30, TargetPeriodMode.CALENDAR),
    EVERY_30_DAYS(30, TargetPeriodMode.ROLLING);

    val frequency: Frequency
        get() = Frequency(1, denominator)

    val label: FrequencyLabel
        get() = FrequencyLabel.of(frequency, true, mode ?: TargetPeriodMode.CALENDAR)

    fun targetPeriodMode(stored: TargetPeriodMode): TargetPeriodMode = mode ?: stored
}
