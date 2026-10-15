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
package org.isoron.uhabits.core.ui.screens.habits.show.views

import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.BaseUnitTest
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.ui.NumberField
import org.isoron.uhabits.core.ui.NumberInput
import org.isoron.uhabits.core.ui.callbacks.NumberPickerCallback
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryCardPresenterTest : BaseUnitTest() {
    private val screen: HistoryCardPresenter.Screen = mock()
    private lateinit var habit: Habit
    private lateinit var presenter: HistoryCardPresenter

    @BeforeTest
    override fun setUp() {
        super.setUp()
        habit = fixtures.createNumericalHabit()
        habitList.add(habit)
        presenter = HistoryCardPresenter(commandRunner, habit, habitList, mock(), screen)
    }

    @Test
    fun testOnDateLongPress_skippedDayOpensEmptyWithSkippedHint() {
        val (field, _) = openNumberPopup(Entry(getToday(), Entry.SKIP, null))
        assertEquals(NumberField("", isSkipped = true), field)
    }

    @Test
    fun testOnDateLongPress_savingEmptyFieldKeepsStateAndSavesNotes() {
        val (_, picker) = openNumberPopup(Entry(getToday(), Entry.SKIP, null))
        picker.onNumberPicked(NumberInput.Typed(""), "sick")
        assertEquals(Entry(getToday(), Entry.SKIP, null, "sick"), todayEntry())
    }

    @Test
    fun testOnDateLongPress_skipWritesTheSkipState() {
        val (_, picker) = openNumberPopup(Entry(getToday(), Entry.AMOUNT, 2500))
        picker.onNumberPicked(NumberInput.Skip, "")
        assertEquals(Entry(getToday(), Entry.SKIP, null), todayEntry())
    }

    @Test
    fun testOnDateLongPress_typedAmountIsWritten() {
        val (_, picker) = openNumberPopup(Entry(getToday(), Entry.UNKNOWN, null))
        picker.onNumberPicked(NumberInput.Typed("2.5"), "")
        assertEquals(Entry(getToday(), Entry.AMOUNT, 2500), todayEntry())
    }

    private fun openNumberPopup(today: Entry): Pair<NumberField, NumberPickerCallback> {
        var field: NumberField? = null
        var picker: NumberPickerCallback? = null
        every { screen.showFeedback() } returns Unit
        every { screen.showNumberPopup(any(), any(), any()) } calls { args ->
            field = args.arg<NumberField>(0)
            picker = args.arg<NumberPickerCallback>(2)
            Unit
        }
        habit.originalEntries.add(today)
        habit.recompute()
        presenter.onDateLongPress(getToday())
        return field!! to picker!!
    }

    private fun todayEntry(): Entry = habit.originalEntries.get(getToday())
}
