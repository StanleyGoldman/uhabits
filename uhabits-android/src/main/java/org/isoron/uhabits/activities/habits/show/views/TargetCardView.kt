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
package org.isoron.uhabits.activities.habits.show.views

import android.content.Context
import android.content.res.Resources
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import org.isoron.platform.gui.toInt
import org.isoron.uhabits.R
import org.isoron.uhabits.core.ui.screens.habits.show.views.TargetCardState
import org.isoron.uhabits.core.ui.screens.habits.show.views.TargetInterval
import org.isoron.uhabits.databinding.ShowHabitTargetBinding

class TargetCardView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {
    private val binding = ShowHabitTargetBinding.inflate(LayoutInflater.from(context), this)
    fun setState(state: TargetCardState) {
        val androidColor = state.theme.color(state.color).toInt()
        binding.targetChart.setValues(state.values)
        binding.targetChart.setTargets(state.targets)
        binding.targetChart.setLabels(state.intervals.map { intervalToLabel(resources, it) })
        binding.title.setTextColor(androidColor)
        binding.targetChart.setColor(androidColor)
        postInvalidate()
    }

    companion object {
        fun intervalToLabel(resources: Resources, interval: TargetInterval) = when (interval) {
            TargetInterval.TODAY -> resources.getString(R.string.today)
            TargetInterval.WEEK -> resources.getString(R.string.week)
            TargetInterval.LAST_7_DAYS -> resources.getString(R.string.last_7_days)
            TargetInterval.MONTH -> resources.getString(R.string.month)
            TargetInterval.LAST_30_DAYS -> resources.getString(R.string.last_30_days)
            TargetInterval.QUARTER -> resources.getString(R.string.quarter)
            TargetInterval.YEAR -> resources.getString(R.string.year)
        }
    }
}
