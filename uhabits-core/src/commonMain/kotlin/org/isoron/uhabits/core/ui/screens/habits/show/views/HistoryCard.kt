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

import org.isoron.platform.time.DayOfWeek
import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.commands.CommandRunner
import org.isoron.uhabits.core.commands.CreateRepetitionCommand
import org.isoron.uhabits.core.models.DayStatus
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Entry.Companion.SKIP
import org.isoron.uhabits.core.models.Entry.Companion.YES_AUTO
import org.isoron.uhabits.core.models.Entry.Companion.YES_MANUAL
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitList
import org.isoron.uhabits.core.models.NumericalHabitType.AT_LEAST
import org.isoron.uhabits.core.models.NumericalHabitType.AT_MOST
import org.isoron.uhabits.core.models.PaletteColor
import org.isoron.uhabits.core.models.TargetPeriodMode.CALENDAR
import org.isoron.uhabits.core.preferences.Preferences
import org.isoron.uhabits.core.ui.NumberField
import org.isoron.uhabits.core.ui.callbacks.CheckMarkDialogCallback
import org.isoron.uhabits.core.ui.callbacks.NumberPickerCallback
import org.isoron.uhabits.core.ui.views.HistoryChart
import org.isoron.uhabits.core.ui.views.HistoryChart.Square.DIMMED
import org.isoron.uhabits.core.ui.views.HistoryChart.Square.DIMMED_OUTLINED
import org.isoron.uhabits.core.ui.views.HistoryChart.Square.GREY
import org.isoron.uhabits.core.ui.views.HistoryChart.Square.GREY_HATCHED
import org.isoron.uhabits.core.ui.views.HistoryChart.Square.GREY_OVER_CAP
import org.isoron.uhabits.core.ui.views.HistoryChart.Square.HATCHED
import org.isoron.uhabits.core.ui.views.HistoryChart.Square.OFF
import org.isoron.uhabits.core.ui.views.HistoryChart.Square.OFF_OUTLINED
import org.isoron.uhabits.core.ui.views.HistoryChart.Square.ON
import org.isoron.uhabits.core.ui.views.OnDateClickedListener
import org.isoron.uhabits.core.ui.views.Theme

data class HistoryCardState(
    val color: PaletteColor,
    val firstWeekday: DayOfWeek,
    val series: List<HistoryChart.Square>,
    val defaultSquare: HistoryChart.Square,
    val notesIndicators: List<Boolean>,
    val theme: Theme,
    val today: LocalDate
)

class HistoryCardPresenter(
    val commandRunner: CommandRunner,
    val habit: Habit,
    val habitList: HabitList,
    val preferences: Preferences,
    val screen: Screen
) : OnDateClickedListener {

    override fun onDateLongPress(date: LocalDate) {
        screen.showFeedback()
        if (habit.isNumerical) {
            showNumberPopup(date)
        } else {
            if (preferences.isShortToggleEnabled) {
                showCheckmarkPopup(date)
            } else {
                toggle(date)
            }
        }
    }

    override fun onDateShortPress(date: LocalDate) {
        screen.showFeedback()
        if (habit.isNumerical) {
            showNumberPopup(date)
        } else {
            if (preferences.isShortToggleEnabled) {
                toggle(date)
            } else {
                showCheckmarkPopup(date)
            }
        }
    }

    private fun showCheckmarkPopup(date: LocalDate) {
        val entry = habit.computedEntries.get(date)
        screen.showCheckmarkPopup(
            entry.state,
            entry.notes,
            habit.color
        ) { newState, newNotes ->
            commandRunner.run(
                CreateRepetitionCommand(
                    habitList,
                    habit,
                    date,
                    newState,
                    amount = null,
                    notes = newNotes
                )
            )
        }
    }

    private fun toggle(date: LocalDate) {
        val entry = habit.computedEntries.get(date)
        val nextState = Entry.nextToggleValue(
            state = entry.state,
            isSkipEnabled = preferences.isSkipEnabled,
            areQuestionMarksEnabled = preferences.areQuestionMarksEnabled
        )
        commandRunner.run(
            CreateRepetitionCommand(
                habitList,
                habit,
                date,
                nextState,
                amount = null,
                notes = entry.notes
            )
        )
    }

    private fun showNumberPopup(date: LocalDate) {
        val entry = habit.computedEntries.get(date)
        screen.showNumberPopup(
            field = NumberField.of(entry),
            notes = entry.notes
        ) { input, newNotes ->
            commandRunner.run(
                CreateRepetitionCommand(habitList, habit, input.toEntry(entry, newNotes))
            )
        }
    }

    fun onClickEditButton() {
        screen.showHistoryEditorDialog(this)
    }

    companion object {
        fun buildState(
            habit: Habit,
            firstWeekday: DayOfWeek,
            theme: Theme
        ): HistoryCardState {
            val today = getToday()
            val oldestKnown = habit.computedEntries.getKnown().lastOrNull()?.date ?: today
            // A calendar week or month is drawn whole, from the start of the oldest entry's.
            val drawsWholePeriods = habit.isJudgedByPeriod && habit.targetPeriodMode == CALENDAR
            val oldest = if (drawsWholePeriods) habit.targetPeriod(oldestKnown)!!.start else oldestKnown
            val entries = habit.computedEntries.getByInterval(oldest, today)
            val series = if (habit.isJudgedByPeriod) {
                entries.map { periodSquare(habit, it) }
            } else if (habit.isNumerical) {
                entries.map {
                    when {
                        it.state == Entry.UNKNOWN -> OFF
                        it.state == SKIP -> HATCHED
                        (habit.targetType == AT_MOST) && (it.amount!! / 1000.0 <= habit.targetValue) -> ON
                        (habit.targetType == AT_LEAST) && (it.amount!! / 1000.0 >= habit.targetValue) -> ON
                        else -> GREY
                    }
                }
            } else {
                entries.map {
                    when (it.state) {
                        YES_MANUAL -> ON
                        YES_AUTO -> DIMMED
                        SKIP -> HATCHED
                        else -> OFF
                    }
                }
            }
            val notesIndicators = entries.map {
                when (it.notes) {
                    "" -> false
                    else -> true
                }
            }

            return HistoryCardState(
                color = habit.color,
                firstWeekday = firstWeekday,
                today = today,
                theme = theme,
                series = series,
                defaultSquare = OFF,
                notesIndicators = notesIndicators
            )
        }

        /**
         * The square of a day of a weekly or monthly measurable habit, drawn
         * from its period's status and what was entered on the day.
         */
        private fun periodSquare(habit: Habit, entry: Entry): HistoryChart.Square {
            val state = entry.state
            val status = habit.statusOn(entry.date)
            if (state == Entry.SKIP) return if (status == DayStatus.MISSED) GREY_HATCHED else HATCHED
            return when (status) {
                DayStatus.MET -> if (state == Entry.AMOUNT) ON else DIMMED
                DayStatus.OPEN -> if (state == Entry.AMOUNT) DIMMED_OUTLINED else OFF_OUTLINED
                else -> when {
                    state != Entry.AMOUNT -> OFF
                    habit.wentOverCapOn(entry.date) -> GREY_OVER_CAP
                    else -> GREY
                }
            }
        }
    }

    interface Screen {
        fun showHistoryEditorDialog(listener: OnDateClickedListener)
        fun showFeedback()
        fun showNumberPopup(
            field: NumberField,
            notes: String,
            callback: NumberPickerCallback
        )
        fun showCheckmarkPopup(
            selectedValue: Int,
            notes: String,
            color: PaletteColor,
            callback: CheckMarkDialogCallback
        )
    }
}
