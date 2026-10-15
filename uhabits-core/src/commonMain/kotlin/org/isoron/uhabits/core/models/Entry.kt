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

/**
 * What the user recorded on one day: a [state], an [amount] when the state is
 * [AMOUNT], and notes.
 *
 * Measurable entries are [AMOUNT], [SKIP] or [UNKNOWN]; yes-or-no entries are
 * [YES_MANUAL], [NO], [SKIP] or [UNKNOWN], and [YES_AUTO] is only computed.
 */
data class Entry(
    val date: LocalDate,
    val state: Int,
    /** The amount ×1000, non-null only when [state] is [AMOUNT]. */
    val amount: Int?,
    val notes: String = ""
) {
    /**
     * Builds an entry in the old single-value form, where a measurable habit's
     * amount sits in [value]. Storage converts it with [toStateForm].
     */
    constructor(date: LocalDate, value: Int, notes: String = "") :
        this(date, value, null, notes)

    /**
     * Compatibility view of the old single-value form: the amount when there
     * is one, otherwise the state. Readers move to [state] and [amount].
     */
    val value: Int
        get() = amount ?: state

    /**
     * Converts an entry that may be in the old single-value form into state
     * and amount, using the same rules as migration 26.
     */
    fun toStateForm(isNumerical: Boolean): Entry {
        if (!isNumerical || amount != null) return this
        return when (state) {
            SKIP, UNKNOWN -> this
            YES_MANUAL -> copy(state = AMOUNT, amount = 1000)
            else -> copy(state = AMOUNT, amount = state)
        }
    }

    val formattedValue: String
        get() = when (value) {
            YES_MANUAL -> "YES_MANUAL"
            YES_AUTO -> "YES_AUTO"
            NO -> "NO"
            SKIP -> "SKIP"
            UNKNOWN -> "UNKNOWN"
            else -> value.toString()
        }

    companion object {
        /**
         * State of a measurable entry that records an amount.
         */
        const val AMOUNT = 4

        /**
         * Value indicating that the habit is not applicable for this timestamp.
         */
        const val SKIP = 3

        /**
         * Value indicating that the user has performed the habit at this timestamp.
         */
        const val YES_MANUAL = 2

        /**
         * Value indicating that the user did not perform the habit, but they were not
         * expected to, because of the frequency of the habit.
         */
        const val YES_AUTO = 1

        /**
         * Value indicating that the user did not perform the habit, even though they were
         * expected to perform it.
         */
        const val NO = 0

        /**
         * Value indicating that no data is available for the given timestamp.
         */
        const val UNKNOWN = -1

        fun nextToggleValue(
            value: Int,
            isSkipEnabled: Boolean,
            areQuestionMarksEnabled: Boolean
        ): Int {
            return when (value) {
                YES_AUTO -> YES_MANUAL
                YES_MANUAL -> if (isSkipEnabled) SKIP else NO
                SKIP -> NO
                NO -> if (areQuestionMarksEnabled) UNKNOWN else YES_MANUAL
                UNKNOWN -> YES_MANUAL
                else -> YES_MANUAL
            }
        }
    }
}
