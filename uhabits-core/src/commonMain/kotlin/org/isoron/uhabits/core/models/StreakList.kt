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
import org.isoron.platform.time.getToday
import kotlin.math.min

class StreakList {
    private val list = ArrayList<Streak>()

    @Synchronized
    fun getBest(limit: Int): List<Streak> {
        list.sortWith { s1: Streak, s2: Streak -> s2.compareLonger(s1) }
        return list.subList(0, min(list.size, limit)).apply {
            sortWith { s1: Streak, s2: Streak -> s2.compareNewer(s1) }
        }.toList()
    }

    /**
     * Rebuilds the streaks from the habit's verdicts between [from] and [to].
     * A calendar week or month is judged as a whole: once met, all its days
     * join the streak. An open day or period is pending: only the current
     * one or those to come can be open, so the streak ends just before it
     * until it is met. A period of only skips bridges the streak like a
     * skipped day.
     */
    @Synchronized
    fun recompute(habit: Habit, from: LocalDate, to: LocalDate) {
        list.clear()
        val unit = habit.calendarPeriodUnit
        var begin: LocalDate? = null
        var end: LocalDate? = null
        var periodCount = 0
        fun close() {
            if (begin != null) list.add(Streak(begin!!, end!!, periodCount, unit))
            begin = null
            periodCount = 0
        }
        // A measurable day judged alone or by its rolling window is pending
        // until it comes, even if its window already holds enough.
        val last = if (habit.isNumerical && unit == null) minOf(to, getToday()) else to
        var date = from
        while (date <= last) {
            val span = habit.calendarPeriod(date) ?: date..date
            when (habit.statusOn(date)) {
                DayStatus.MET, null -> {
                    if (begin == null) begin = span.start
                    end = span.endInclusive
                    if (unit != null) periodCount++
                }
                else -> close()
            }
            date = span.endInclusive.plus(1)
        }
        close()
    }
}
