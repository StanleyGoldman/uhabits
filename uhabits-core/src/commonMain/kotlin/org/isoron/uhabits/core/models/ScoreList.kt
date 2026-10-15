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

import org.isoron.platform.Synchronized
import org.isoron.platform.time.LocalDate
import org.isoron.uhabits.core.models.Score.Companion.compute
import kotlin.math.min

class ScoreList {

    private val map = mutableMapOf<LocalDate, Score>()

    /**
     * Returns the score for a given day. If the date given happens before the first
     * repetition of the habit or after the last computed score, returns a score with value zero.
     */
    @Synchronized
    operator fun get(date: LocalDate): Score {
        return map[date] ?: Score(date, 0.0)
    }

    /**
     * Returns the list of scores that fall within the given interval.
     *
     * There is exactly one score per day in the interval. The endpoints of the interval are
     * included. The list is ordered by date (decreasing). That is, the first score
     * corresponds to the newest date, and the last score corresponds to the oldest date.
     */
    @Synchronized
    fun getByInterval(
        from: LocalDate,
        to: LocalDate
    ): List<Score> {
        val result: MutableList<Score> = mutableListOf()
        if (from.isNewerThan(to)) return result
        var current = to
        while (!current.isOlderThan(from)) {
            result.add(get(current))
            current = current.minus(1)
        }
        return result
    }

    /**
     * Recomputes all scores between the provided [from] and [to] dates.
     *
     * With [assumeNone] off, an "at most" habit starts at zero, and a day before [today] with
     * nothing entered grades zero. Days from [today] on with nothing entered are still open,
     * so they leave the score unchanged.
     */
    @Synchronized
    fun recompute(
        frequency: Frequency,
        isNumerical: Boolean,
        numericalHabitType: NumericalHabitType,
        targetValue: Double,
        assumeNone: Boolean,
        computedEntries: EntryList,
        from: LocalDate,
        to: LocalDate,
        today: LocalDate
    ) {
        map.clear()
        val freq = frequency.toDouble()
        val isAtMost = isNumerical && numericalHabitType == NumericalHabitType.AT_MOST
        val entries = computedEntries.getByInterval(from, to)
            .map { it.toStateForm(isNumerical) }
            .reversed()

        // What each day adds to the window: the amount ×1000 for numerical habits, one per
        // manual check for boolean habits. Skips and unknown days add nothing.
        val points = entries.map {
            when {
                isNumerical && it.state == Entry.AMOUNT -> it.amount!!.toLong()
                !isNumerical && it.state == Entry.YES_MANUAL -> 1L
                else -> 0L
            }
        }

        var windowLength = frequency.denominator
        var windowTarget = if (isNumerical) targetValue * 1000 else frequency.numerator.toDouble()

        // For non-daily habits, we double the window and its target to smooth out irregular
        // repetition schedules (for example, weekly habits performed on different days of
        // the week)
        if (freq < 1.0) {
            windowLength *= 2
            windowTarget *= 2
        }

        var rollingSum = 0L
        var rollingSkips = 0
        val isAtMostWithoutAssumeNone = isAtMost && !assumeNone
        var previousValue = if (isAtMost && assumeNone) 1.0 else 0.0
        for (i in entries.indices) {
            rollingSum += points[i]
            if (entries[i].state == Entry.SKIP) rollingSkips++
            if (i >= windowLength) {
                rollingSum -= points[i - windowLength]
                if (entries[i - windowLength].state == Entry.SKIP) rollingSkips--
            }

            // Skipped days leave the score unchanged. The other days are graded against the
            // target scaled down to the days in the window that aren't skips, so that skips
            // don't take up slots in the window.
            val date = from.plus(i)
            val isNothingEntered = entries[i].state == Entry.UNKNOWN
            if (isAtMostWithoutAssumeNone && isNothingEntered) {
                if (date.isOlderThan(today)) previousValue = compute(freq, previousValue, 0.0)
            } else if (entries[i].state != Entry.SKIP) {
                val daysThatCount = windowLength - rollingSkips
                val percentageCompleted = if (windowTarget > 0) {
                    val ratio = (rollingSum * windowLength) / (windowTarget * daysThatCount)
                    if (isAtMost) (2 - ratio).coerceIn(0.0, 1.0) else min(1.0, ratio)
                } else {
                    if (isAtMost && rollingSum > 0) 0.0 else 1.0
                }
                previousValue = compute(freq, previousValue, percentageCompleted)
            }
            map[date] = Score(date, previousValue)
        }
    }
}
