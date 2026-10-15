/*
 * Copyright (C) 2016-2026 Álinson Santos Xavier <git@axavier.org>
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
 * How a weekly or monthly measurable habit's target period is laid out:
 * calendar weeks and months, or a rolling window of the last 7 or 30 days.
 */
enum class TargetPeriodMode(val value: Int) {
    CALENDAR(0), ROLLING(1);

    companion object {
        fun fromInt(value: Int): TargetPeriodMode {
            return when (value) {
                CALENDAR.value -> CALENDAR
                ROLLING.value -> ROLLING
                else -> throw IllegalStateException()
            }
        }
    }
}
