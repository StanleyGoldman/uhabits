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
 * How a habit's frequency reads to the user. Each platform maps a label to
 * its own localized string.
 */
sealed class FrequencyLabel {
    object EveryDay : FrequencyLabel()
    object EveryWeek : FrequencyLabel()
    object EveryMonth : FrequencyLabel()
    data class EveryXDays(val days: Int) : FrequencyLabel()
    data class XTimesPerWeek(val times: Int) : FrequencyLabel()
    data class XTimesPerMonth(val times: Int) : FrequencyLabel()
    data class XTimesPerYDays(val times: Int, val days: Int) : FrequencyLabel()

    companion object {
        fun of(habit: Habit): FrequencyLabel =
            of(habit.frequency, habit.isNumerical, habit.targetPeriodMode)

        /**
         * The target period mode only applies to weekly and monthly
         * measurable habits; a rolling one reads "Every 7 days" or
         * "Every 30 days".
         */
        fun of(
            frequency: Frequency,
            isNumerical: Boolean = false,
            targetPeriodMode: TargetPeriodMode = TargetPeriodMode.CALENDAR
        ): FrequencyLabel {
            val num = frequency.numerator
            val den = frequency.denominator
            val isRolling = isNumerical && targetPeriodMode == TargetPeriodMode.ROLLING
            if (isRolling && num == 1 && den > 1) return EveryXDays(den)
            return ofFrequency(frequency)
        }

        private fun ofFrequency(frequency: Frequency): FrequencyLabel {
            val num = frequency.numerator
            val den = frequency.denominator
            return when {
                num == 1 && (den == 30 || den == 31) -> EveryMonth
                den == 30 || den == 31 -> XTimesPerMonth(num)
                num == 1 && den == 1 -> EveryDay
                num == 1 && den == 7 -> EveryWeek
                num == 1 && den > 1 -> EveryXDays(den)
                den == 7 -> XTimesPerWeek(num)
                else -> XTimesPerYDays(num, den)
            }
        }
    }
}
