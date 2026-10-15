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
package org.isoron.uhabits.core.commands

import org.isoron.platform.time.LocalDate
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitList

data class CreateRepetitionCommand(
    val habitList: HabitList,
    val habit: Habit,
    val date: LocalDate,
    val state: Int,
    /** The amount ×1000, non-null only when [state] is [Entry.AMOUNT]. */
    val amount: Int?,
    val notes: String
) : Command {
    /** The old form: [value] is an old-form entry value, as [Entry]'s secondary constructor takes it. */
    constructor(habitList: HabitList, habit: Habit, date: LocalDate, value: Int, notes: String) :
        this(habitList, habit, date, value, null, notes)

    /** Records [entry], in state and amount form, on its date. */
    constructor(habitList: HabitList, habit: Habit, entry: Entry) :
        this(habitList, habit, entry.date, entry.state, entry.amount, entry.notes)

    override fun run() {
        val entries = habit.originalEntries
        entries.add(Entry(date, state, amount, notes))
        habit.recompute()
        habitList.resort()
    }
}
