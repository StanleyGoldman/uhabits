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
import org.isoron.platform.time.getFirstWeekday
import org.isoron.platform.time.getToday
import kotlin.math.roundToLong
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class Habit(
    var color: PaletteColor = PaletteColor(8),
    var description: String = "",
    var frequency: Frequency = Frequency.DAILY,
    var id: Long? = null,
    var isArchived: Boolean = false,
    var name: String = "",
    var position: Int = 0,
    var question: String = "",
    var reminder: Reminder? = null,
    var targetType: NumericalHabitType = NumericalHabitType.AT_LEAST,
    var targetValue: Double = 0.0,
    var type: HabitType = HabitType.YES_NO,
    var unit: String = "",
    var uuid: String? = null,
    var targetPeriodMode: TargetPeriodMode = TargetPeriodMode.CALENDAR,
    var assumeNone: Boolean = true,
    var skipReminderIfLogged: Boolean = false,
    val computedEntries: EntryList,
    val originalEntries: EntryList,
    val scores: ScoreList,
    val streaks: StreakList
) {
    init {
        if (uuid == null) this.uuid = Uuid.random().toHexString()
    }

    var observable = ModelObservable()

    val isNumerical: Boolean
        get() = type == HabitType.NUMERICAL

    val uriString: String
        get() = "content://org.isoron.uhabits/habit/$id"

    fun hasReminder(): Boolean = reminder != null

    /**
     * How the habit stands today. A weekly or monthly measurable habit is
     * judged by its target period; an all-skipped period counts as met, as a
     * skipped day does.
     */
    fun todayStatus(): DayStatus = statusOn(getToday()) ?: DayStatus.MET

    /**
     * How the habit stands on [date], judged as of today: a day, or the
     * target period of a weekly or monthly measurable habit. Null when that
     * period's days are all skipped, so it has no verdict.
     */
    fun statusOn(date: LocalDate): DayStatus? {
        if (!isNumerical) return yesOrNoStatus(date)
        val period = targetPeriod(date) ?: return measurableDayStatus(date)
        return periodStatus(period)
    }

    private fun yesOrNoStatus(date: LocalDate): DayStatus {
        return when (computedEntries.get(date).value) {
            Entry.UNKNOWN -> if (date < getToday()) DayStatus.MISSED else DayStatus.OPEN
            Entry.NO -> DayStatus.MISSED
            else -> DayStatus.MET
        }
    }

    private fun measurableDayStatus(date: LocalDate): DayStatus {
        val entry = computedEntries.get(date).toStateForm(isNumerical = true)
        if (entry.state == Entry.SKIP) return DayStatus.MET
        val isClosed = date < getToday()
        if (entry.state == Entry.UNKNOWN) {
            return when {
                !isClosed -> DayStatus.OPEN
                targetType == NumericalHabitType.AT_MOST && assumeNone -> DayStatus.MET
                else -> DayStatus.MISSED
            }
        }
        return judge(entry.amount!!.toLong(), targetX1000(), isClosed)
    }

    /** The target or cap ×1000, the scale amounts are stored in. */
    private fun targetX1000(): Long = (targetValue * 1000).roundToLong()

    /**
     * Judges a total against a target or cap, both scaled to the same days.
     * An "at least" total is met on reaching the target and missed only once
     * closed; an "at most" total is missed on going over the cap and met only
     * once closed. With [isEntered] false nothing was entered, which never
     * meets an "at least" target, not even a target of 0.
     */
    private fun judge(
        total: Long,
        target: Long,
        isClosed: Boolean,
        isEntered: Boolean = true
    ): DayStatus {
        return when (targetType) {
            NumericalHabitType.AT_LEAST -> when {
                isEntered && total >= target -> DayStatus.MET
                isClosed -> DayStatus.MISSED
                else -> DayStatus.OPEN
            }
            NumericalHabitType.AT_MOST -> when {
                total > target -> DayStatus.MISSED
                isClosed -> DayStatus.MET
                else -> DayStatus.OPEN
            }
        }
    }

    /**
     * The length of a weekly or monthly measurable habit's target period, or
     * null for a daily or yes-or-no habit. A monthly habit may be stored as
     * 1/30 or 1/31.
     */
    private val periodUnit: PeriodUnit?
        get() {
            if (!isNumerical) return null
            return when (frequency.denominator) {
                7 -> PeriodUnit.WEEK
                30, 31 -> PeriodUnit.MONTH
                else -> null
            }
        }

    /**
     * The days whose total is judged against the target on [date]: the
     * calendar week or month holding it, the rolling window of the last 7 or
     * 30 days ending on it, or null for a daily habit. A rolling month is
     * 30 days however the frequency is stored.
     */
    private fun targetPeriod(date: LocalDate): ClosedRange<LocalDate>? {
        val isRolling = targetPeriodMode == TargetPeriodMode.ROLLING
        return when (periodUnit) {
            PeriodUnit.WEEK -> when {
                isRolling -> date.minus(6)..date
                else -> date.startOfWeek(getFirstWeekday()).let { it..it.plus(6) }
            }
            PeriodUnit.MONTH -> when {
                isRolling -> date.minus(29)..date
                else -> date.startOfMonth().let { it..it.plus(it.monthLength - 1) }
            }
            null -> null
        }
    }

    /** The unit of [calendarPeriod], or null when the habit has none. */
    internal val calendarPeriodUnit: PeriodUnit?
        get() = periodUnit.takeIf { targetPeriodMode == TargetPeriodMode.CALENDAR }

    /**
     * The calendar week or month holding [date] for a weekly or monthly
     * measurable habit in calendar mode, or null when each day is judged on
     * its own window or by itself.
     */
    internal fun calendarPeriod(date: LocalDate): ClosedRange<LocalDate>? {
        if (calendarPeriodUnit == null) return null
        return targetPeriod(date)
    }

    /**
     * The verdict of a target period, or null when every day in it is
     * skipped. A skipped day drops out, and the target scales to the days
     * that count; days still to come count. Totals are compared as ×1000
     * integers, `total × days` against `target × days that count`, so nothing
     * rounds.
     */
    private fun periodStatus(period: ClosedRange<LocalDate>): DayStatus? {
        val entries = computedEntries.getByInterval(period.start, period.endInclusive)
            .map { it.toStateForm(isNumerical = true) }
        val days = period.start.daysUntil(period.endInclusive) + 1L
        val daysThatCount = days - entries.count { it.state == Entry.SKIP }
        if (daysThatCount == 0L) return null
        val amounts = entries.filter { it.state == Entry.AMOUNT }.map { it.amount!!.toLong() }
        return judge(
            total = amounts.sum() * days,
            target = targetX1000() * daysThatCount,
            isClosed = period.endInclusive < getToday(),
            isEntered = amounts.isNotEmpty()
        )
    }

    fun isEnteredToday(): Boolean {
        val today = getToday()
        val value = originalEntries.get(today).value
        return value != Entry.UNKNOWN
    }

    fun recompute() {
        computedEntries.recomputeFrom(
            originalEntries = originalEntries,
            frequency = frequency,
            isNumerical = isNumerical
        )

        val today = getToday()
        val to = today.plus(30)
        val entries = computedEntries.getKnown()
        var from = entries.lastOrNull()?.date ?: today
        if (from.isNewerThan(to)) from = to

        scores.recompute(
            frequency = frequency,
            isNumerical = isNumerical,
            numericalHabitType = targetType,
            targetValue = targetValue,
            assumeNone = assumeNone,
            computedEntries = computedEntries,
            from = from,
            to = to,
            today = today
        )

        streaks.recompute(this, from, to, today)
    }

    fun copyFrom(other: Habit) {
        this.color = other.color
        this.description = other.description
        this.frequency = other.frequency
        // this.id should not be copied
        this.isArchived = other.isArchived
        this.name = other.name
        this.position = other.position
        this.question = other.question
        this.reminder = other.reminder
        this.targetType = other.targetType
        this.targetValue = other.targetValue
        this.type = other.type
        this.unit = other.unit
        this.uuid = other.uuid
        this.targetPeriodMode = other.targetPeriodMode
        this.assumeNone = other.assumeNone
        this.skipReminderIfLogged = other.skipReminderIfLogged
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Habit) return false

        if (color != other.color) return false
        if (description != other.description) return false
        if (frequency != other.frequency) return false
        if (id != other.id) return false
        if (isArchived != other.isArchived) return false
        if (name != other.name) return false
        if (position != other.position) return false
        if (question != other.question) return false
        if (reminder != other.reminder) return false
        if (targetType != other.targetType) return false
        if (targetValue != other.targetValue) return false
        if (type != other.type) return false
        if (unit != other.unit) return false
        if (uuid != other.uuid) return false
        if (targetPeriodMode != other.targetPeriodMode) return false
        if (assumeNone != other.assumeNone) return false
        if (skipReminderIfLogged != other.skipReminderIfLogged) return false

        return true
    }

    override fun hashCode(): Int {
        var result = color.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + frequency.hashCode()
        result = 31 * result + (id?.hashCode() ?: 0)
        result = 31 * result + isArchived.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + position
        result = 31 * result + question.hashCode()
        result = 31 * result + (reminder?.hashCode() ?: 0)
        result = 31 * result + targetType.value
        result = 31 * result + targetValue.hashCode()
        result = 31 * result + type.value
        result = 31 * result + unit.hashCode()
        result = 31 * result + (uuid?.hashCode() ?: 0)
        result = 31 * result + targetPeriodMode.value
        result = 31 * result + assumeNone.hashCode()
        result = 31 * result + skipReminderIfLogged.hashCode()
        return result
    }
}
