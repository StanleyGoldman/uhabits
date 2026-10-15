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
package org.isoron.uhabits.core.ui

import org.isoron.uhabits.core.models.Entry
import kotlin.math.roundToInt

/**
 * What the number dialog shows for one day of a measurable habit: the amount
 * as text with up to two decimals, or an empty field on a skipped or unknown
 * day. [isSkipped] asks the dialog to hint "Skipped".
 */
data class NumberField(val text: String, val isSkipped: Boolean) {
    companion object {
        fun of(entry: Entry): NumberField {
            val amount = entry.toStateForm(isNumerical = true).amount
                ?: return NumberField("", isSkipped = entry.state == Entry.SKIP)
            return NumberField(formatHundredths((amount + 5).floorDiv(10)), false)
        }

        private fun formatHundredths(hundredths: Int): String {
            val whole = hundredths / 100
            val cents = hundredths % 100
            return when {
                cents == 0 -> "$whole"
                cents % 10 == 0 -> "$whole.${cents / 10}"
                else -> "$whole.${cents.toString().padStart(2, '0')}"
            }
        }
    }
}

/** What the user did in the number dialog. */
sealed interface NumberInput {
    /** Saved the field with [text] in it, which may be empty. */
    data class Typed(val text: String) : NumberInput
    data object Skip : NumberInput
    data object Unknown : NumberInput

    /** The entry this input records on the day whose entry was [previous]. */
    fun toEntry(previous: Entry, notes: String): Entry {
        val before = previous.toStateForm(isNumerical = true).copy(notes = notes)
        return when (this) {
            is Typed -> {
                val number = text.toDoubleOrNull()
                when {
                    text.isEmpty() && before.amount != null -> before.copy(state = Entry.UNKNOWN, amount = null)
                    number == null || text == NumberField.of(previous).text -> before
                    else -> before.copy(state = Entry.AMOUNT, amount = (number * 100).roundToInt() * 10)
                }
            }
            Skip -> before.copy(state = Entry.SKIP, amount = null)
            Unknown -> before.copy(state = Entry.UNKNOWN, amount = null)
        }
    }
}
