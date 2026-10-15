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

package org.isoron.uhabits.activities.habits.edit

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.content.res.Resources
import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.CompoundButtonCompat
import androidx.fragment.app.DialogFragment
import com.android.datetimepicker.time.RadialPickerLayout
import com.android.datetimepicker.time.TimePickerDialog
import org.isoron.platform.gui.toInt
import org.isoron.platform.time.JavaLocalDateFormatter
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.AndroidThemeSwitcher
import org.isoron.uhabits.activities.common.dialogs.ColorPickerDialogFactory
import org.isoron.uhabits.activities.common.dialogs.FrequencyPickerDialog
import org.isoron.uhabits.activities.common.dialogs.WeekdayPickerDialog
import org.isoron.uhabits.core.commands.CommandRunner
import org.isoron.uhabits.core.commands.CreateHabitCommand
import org.isoron.uhabits.core.commands.EditHabitCommand
import org.isoron.uhabits.core.models.Frequency
import org.isoron.uhabits.core.models.FrequencyLabel
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitType
import org.isoron.uhabits.core.models.MeasurableFrequencyOption
import org.isoron.uhabits.core.models.NumericalHabitType
import org.isoron.uhabits.core.models.PaletteColor
import org.isoron.uhabits.core.models.Reminder
import org.isoron.uhabits.core.models.TargetPeriodMode
import org.isoron.uhabits.core.models.WeekdayList
import org.isoron.uhabits.databinding.ActivityEditHabitBinding
import org.isoron.uhabits.utils.StyledResources
import org.isoron.uhabits.utils.applyBottomInset
import org.isoron.uhabits.utils.applyRootViewInsets
import org.isoron.uhabits.utils.applyToolbarInsets
import org.isoron.uhabits.utils.dismissCurrentAndShow
import org.isoron.uhabits.utils.formatTime
import org.isoron.uhabits.utils.toFormattedString
import java.util.Locale

fun formatFrequency(label: FrequencyLabel, resources: Resources): String = when (label) {
    FrequencyLabel.EveryDay -> resources.getString(R.string.every_day)
    FrequencyLabel.EveryWeek -> resources.getString(R.string.every_week)
    FrequencyLabel.EveryMonth -> resources.getString(R.string.every_month)
    is FrequencyLabel.EveryXDays -> resources.getString(R.string.every_x_days, label.days)
    is FrequencyLabel.XTimesPerWeek -> resources.getString(R.string.x_times_per_week, label.times)
    is FrequencyLabel.XTimesPerMonth -> resources.getString(R.string.x_times_per_month, label.times)
    is FrequencyLabel.XTimesPerYDays ->
        resources.getString(R.string.x_times_per_y_days, label.times, label.days)
}

class EditHabitActivity : AppCompatActivity() {

    private lateinit var themeSwitcher: AndroidThemeSwitcher
    private lateinit var binding: ActivityEditHabitBinding
    private lateinit var commandRunner: CommandRunner

    var habitId = -1L
    lateinit var habitType: HabitType
    var unit = ""
    var color = PaletteColor(11)
    var androidColor = 0
    var freqNum = 1
    var freqDen = 1
    var reminderHour = -1
    var reminderMin = -1
    var reminderDays: WeekdayList = WeekdayList.EVERY_DAY
    var targetType = NumericalHabitType.AT_LEAST
    var targetPeriodMode = TargetPeriodMode.CALENDAR
    var assumeNone = true
    var skipReminderIfLogged = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)

        val component = (application as HabitsApplication).component
        themeSwitcher = AndroidThemeSwitcher(this, component.preferences)
        themeSwitcher.apply()

        binding = ActivityEditHabitBinding.inflate(layoutInflater)
        binding.root.applyRootViewInsets()
        binding.root.applyBottomInset()
        binding.toolbar.applyToolbarInsets()
        setContentView(binding.root)

        if (intent.hasExtra("habitId")) {
            binding.toolbar.title = getString(R.string.edit_habit)
            habitId = intent.getLongExtra("habitId", -1)
            val habit = component.habitList.getById(habitId)!!
            habitType = habit.type
            color = habit.color
            freqNum = habit.frequency.numerator
            freqDen = habit.frequency.denominator
            targetType = habit.targetType
            targetPeriodMode = habit.targetPeriodMode
            assumeNone = habit.assumeNone
            skipReminderIfLogged = habit.skipReminderIfLogged
            habit.reminder?.let {
                reminderHour = it.hour
                reminderMin = it.minute
                reminderDays = it.days
            }
            binding.nameInput.setText(habit.name)
            binding.questionInput.setText(habit.question)
            binding.notesInput.setText(habit.description)
            binding.unitInput.setText(habit.unit)
            binding.targetInput.setText(habit.targetValue.toString())
        } else {
            habitType = HabitType.fromInt(intent.getIntExtra("habitType", HabitType.YES_NO.value))
        }

        if (state != null) {
            habitId = state.getLong("habitId")
            habitType = HabitType.fromInt(state.getInt("habitType"))
            color = PaletteColor(state.getInt("paletteColor"))
            freqNum = state.getInt("freqNum")
            freqDen = state.getInt("freqDen")
            reminderHour = state.getInt("reminderHour")
            reminderMin = state.getInt("reminderMin")
            reminderDays = WeekdayList(state.getInt("reminderDays"))
            targetType = NumericalHabitType.fromInt(state.getInt("targetType"))
            targetPeriodMode = TargetPeriodMode.fromInt(state.getInt("targetPeriodMode"))
            assumeNone = state.getBoolean("assumeNone")
            skipReminderIfLogged = state.getBoolean("skipReminderIfLogged")
        }

        updateColors()

        when (habitType) {
            HabitType.YES_NO -> {
                binding.unitOuterBox.visibility = View.GONE
                binding.targetOuterBox.visibility = View.GONE
                binding.targetTypeOuterBox.visibility = View.GONE
            }
            HabitType.NUMERICAL -> {
                binding.nameInput.hint = getString(R.string.measurable_short_example)
                binding.questionInput.hint = getString(R.string.measurable_question_example)
                binding.frequencyOuterBox.visibility = View.GONE
            }
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.elevation = 10.0f

        val colorPickerDialogFactory = ColorPickerDialogFactory(this)
        binding.colorButton.setOnClickListener {
            val picker = colorPickerDialogFactory.create(color, themeSwitcher.currentTheme)
            picker.setListener { paletteColor ->
                this.color = paletteColor
                updateColors()
            }
            picker.dismissCurrentAndShow(supportFragmentManager, "colorPicker")
        }

        populateFrequency()
        binding.booleanFrequencyPicker.setOnClickListener {
            val picker = FrequencyPickerDialog(freqNum, freqDen)
            picker.onFrequencyPicked = { num, den ->
                freqNum = num
                freqDen = den
                populateFrequency()
            }
            picker.dismissCurrentAndShow(supportFragmentManager, "frequencyPicker")
        }

        populateTargetType()
        binding.targetTypePicker.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            val arrayAdapter = ArrayAdapter<String>(this, android.R.layout.select_dialog_item)
            arrayAdapter.add(getString(R.string.target_type_at_least))
            arrayAdapter.add(getString(R.string.target_type_at_most))
            builder.setAdapter(arrayAdapter) { dialog, which ->
                targetType = when (which) {
                    0 -> NumericalHabitType.AT_LEAST
                    else -> NumericalHabitType.AT_MOST
                }
                populateTargetType()
                populateAssumeNone()
                dialog.dismiss()
            }
            val dialog = builder.create()
            dialog.dismissCurrentAndShow()
        }

        binding.numericalFrequencyPicker.setOnClickListener {
            val options = MeasurableFrequencyOption.entries
            val adapter = object : ArrayAdapter<MeasurableFrequencyOption>(
                this,
                R.layout.dialog_item_with_hint,
                android.R.id.text1,
                options
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent)
                    val option = options[position]
                    view.findViewById<TextView>(android.R.id.text1).text =
                        formatFrequency(option.label, resources)
                    val hint = formatFrequencyHint(option)
                    view.findViewById<TextView>(android.R.id.text2).apply {
                        text = hint
                        visibility = if (hint == null) View.GONE else View.VISIBLE
                    }
                    return view
                }
            }
            val builder = AlertDialog.Builder(this)
            builder.setAdapter(adapter) { dialog, which ->
                val option = options[which]
                freqNum = option.frequency.numerator
                freqDen = option.frequency.denominator
                targetPeriodMode = option.targetPeriodMode(stored = targetPeriodMode)
                populateFrequency()
                dialog.dismiss()
            }
            builder.show()
        }

        populateAssumeNone()
        binding.assumeNoneRow.setOnClickListener {
            assumeNone = !assumeNone
            populateAssumeNone()
        }

        binding.skipReminderIfLoggedCheckbox.setOnCheckedChangeListener { _, isChecked ->
            skipReminderIfLogged = isChecked
        }

        populateReminder()
        binding.reminderTimePicker.setOnClickListener {
            val currentHour = if (reminderHour >= 0) reminderHour else 8
            val currentMin = if (reminderMin >= 0) reminderMin else 0
            val is24HourMode = DateFormat.is24HourFormat(this)
            val dialog = TimePickerDialog.newInstance(
                object : TimePickerDialog.OnTimeSetListener {
                    override fun onTimeSet(view: RadialPickerLayout?, hourOfDay: Int, minute: Int) {
                        reminderHour = hourOfDay
                        reminderMin = minute
                        populateReminder()
                    }

                    override fun onTimeCleared(view: RadialPickerLayout?) {
                        reminderHour = -1
                        reminderMin = -1
                        reminderDays = WeekdayList.EVERY_DAY
                        populateReminder()
                    }
                },
                currentHour,
                currentMin,
                is24HourMode,
                androidColor
            )
            dialog.dismissCurrentAndShow(supportFragmentManager, "timePicker")
        }

        binding.reminderDatePicker.setOnClickListener {
            val dialog = WeekdayPickerDialog()

            dialog.setListener { days: WeekdayList ->
                reminderDays = days
                if (reminderDays.isEmpty) reminderDays = WeekdayList.EVERY_DAY
                populateReminder()
            }
            dialog.setSelectedDays(reminderDays)
            dialog.dismissCurrentAndShow(supportFragmentManager, "dayPicker")
        }

        binding.buttonSave.setOnClickListener {
            if (validate()) save()
        }

        for (fragment in supportFragmentManager.fragments) {
            (fragment as DialogFragment).dismiss()
        }
    }

    private fun save() {
        val component = (application as HabitsApplication).component
        val habit = component.modelFactory.buildHabit()

        var original: Habit? = null
        if (habitId >= 0) {
            original = component.habitList.getById(habitId)!!
            habit.copyFrom(original)
        }

        habit.name = binding.nameInput.text.trim().toString()
        habit.question = binding.questionInput.text.trim().toString()
        habit.description = binding.notesInput.text.trim().toString()
        habit.color = color
        if (reminderHour >= 0) {
            habit.reminder = Reminder(reminderHour, reminderMin, reminderDays)
        } else {
            habit.reminder = null
        }

        habit.frequency = Frequency(freqNum, freqDen)
        if (habitType == HabitType.NUMERICAL) {
            habit.targetValue = binding.targetInput.text.toString().toDouble()
            habit.targetType = targetType
            habit.unit = binding.unitInput.text.trim().toString()
            habit.targetPeriodMode = targetPeriodMode
            habit.assumeNone = assumeNone
            habit.skipReminderIfLogged = skipReminderIfLogged
        }
        habit.type = habitType

        val command = if (habitId >= 0) {
            EditHabitCommand(
                component.habitList,
                habitId,
                habit
            )
        } else {
            CreateHabitCommand(
                component.modelFactory,
                component.habitList,
                habit
            )
        }
        component.commandRunner.run(command)
        finish()
    }

    private fun validate(): Boolean {
        var isValid = true
        if (binding.nameInput.text.isEmpty()) {
            binding.nameInput.error = getFormattedValidationError(R.string.validation_cannot_be_blank)
            isValid = false
        }
        if (habitType == HabitType.NUMERICAL) {
            if (binding.targetInput.text.isEmpty()) {
                binding.targetInput.error = getString(R.string.validation_cannot_be_blank)
                isValid = false
            }
        }
        return isValid
    }

    private fun populateReminder() {
        if (reminderHour < 0) {
            binding.reminderTimePicker.text = getString(R.string.reminder_off)
            binding.reminderDatePicker.visibility = View.GONE
            binding.reminderDivider.visibility = View.GONE
        } else {
            val time = formatTime(this, reminderHour, reminderMin)
            binding.reminderTimePicker.text = time
            binding.reminderDatePicker.visibility = View.VISIBLE
            binding.reminderDivider.visibility = View.VISIBLE
            binding.reminderDatePicker.text = reminderDays.toFormattedString(this)
        }
        val showSkip = habitType == HabitType.NUMERICAL && reminderHour >= 0
        val skipVisibility = if (showSkip) View.VISIBLE else View.GONE
        binding.skipReminderIfLoggedDivider.visibility = skipVisibility
        binding.skipReminderIfLoggedCheckbox.visibility = skipVisibility
        binding.skipReminderIfLoggedCheckbox.isChecked = skipReminderIfLogged
    }

    private fun populateAssumeNone() {
        val visibility = if (targetType == NumericalHabitType.AT_MOST) View.VISIBLE else View.GONE
        binding.assumeNoneDivider.visibility = visibility
        binding.assumeNoneRow.visibility = visibility
        binding.assumeNoneSwitch.isChecked = assumeNone
        binding.assumeNoneSubtitle.text = getString(
            if (assumeNone) R.string.assume_none_on else R.string.assume_none_off
        )
    }

    private fun formatFrequencyHint(option: MeasurableFrequencyOption): String? = when (option) {
        MeasurableFrequencyOption.EVERY_DAY -> null
        MeasurableFrequencyOption.EVERY_WEEK -> {
            val firstWeekday = (application as HabitsApplication).component.preferences.firstWeekday
            val names = JavaLocalDateFormatter(Locale.getDefault()).longWeekdayNames(firstWeekday)
            getString(R.string.frequency_hint_calendar_week, names.first(), names.last())
        }
        MeasurableFrequencyOption.EVERY_7_DAYS -> getString(R.string.frequency_hint_rolling, 7)
        MeasurableFrequencyOption.EVERY_MONTH -> getString(R.string.frequency_hint_calendar_month)
        MeasurableFrequencyOption.EVERY_30_DAYS -> getString(R.string.frequency_hint_rolling, 30)
    }

    @SuppressLint("StringFormatMatches")
    private fun populateFrequency() {
        val frequency = Frequency(freqNum, freqDen)
        binding.booleanFrequencyPicker.text = formatFrequency(FrequencyLabel.of(frequency), resources)
        binding.numericalFrequencyPicker.text = formatFrequency(
            FrequencyLabel.of(frequency, isNumerical = true, targetPeriodMode = targetPeriodMode),
            resources
        )
    }

    private fun populateTargetType() {
        binding.targetTypePicker.text = when (targetType) {
            NumericalHabitType.AT_MOST -> getString(R.string.target_type_at_most)
            else -> getString(R.string.target_type_at_least)
        }
    }

    private fun updateColors() {
        androidColor = themeSwitcher.currentTheme.color(color).toInt()
        binding.colorButton.backgroundTintList = ColorStateList.valueOf(androidColor)
        val offColor = StyledResources(this).getColor(R.attr.contrast60)
        val checkedTint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(androidColor, offColor)
        )
        binding.assumeNoneSwitch.thumbTintList = checkedTint
        binding.assumeNoneSwitch.trackTintList = checkedTint
        CompoundButtonCompat.setButtonTintList(binding.skipReminderIfLoggedCheckbox, checkedTint)
        if (!themeSwitcher.isNightMode) {
            window.statusBarColor = androidColor
            binding.toolbar.setBackgroundColor(androidColor)
        }
    }

    private fun getFormattedValidationError(@StringRes resId: Int): Spanned {
        val html = "<font color=#FFFFFF>${getString(resId)}</font>"
        return Html.fromHtml(html)
    }

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        with(state) {
            putLong("habitId", habitId)
            putInt("habitType", habitType.value)
            putInt("paletteColor", color.paletteIndex)
            putInt("androidColor", androidColor)
            putInt("freqNum", freqNum)
            putInt("freqDen", freqDen)
            putInt("reminderHour", reminderHour)
            putInt("reminderMin", reminderMin)
            putInt("reminderDays", reminderDays.toInteger())
            putInt("targetType", targetType.value)
            putInt("targetPeriodMode", targetPeriodMode.value)
            putBoolean("assumeNone", assumeNone)
            putBoolean("skipReminderIfLogged", skipReminderIfLogged)
        }
    }
}
