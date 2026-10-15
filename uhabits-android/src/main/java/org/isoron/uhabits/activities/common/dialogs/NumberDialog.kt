package org.isoron.uhabits.activities.common.dialogs

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.provider.Settings
import android.text.method.DigitsKeyListener
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatDialogFragment
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.R
import org.isoron.uhabits.core.ui.NumberInput
import org.isoron.uhabits.databinding.CheckmarkPopupBinding
import org.isoron.uhabits.utils.InterfaceUtils
import org.isoron.uhabits.utils.requestFocusWithKeyboard
import org.isoron.uhabits.utils.sres
import java.text.DecimalFormatSymbols

class NumberDialog : AppCompatDialogFragment() {

    var onToggle: (NumberInput, String) -> Unit = { _, _ -> }
    var onDismiss: () -> Unit = {}

    private var dismissedViaSaveAction = false
    private var originalNotes: String = ""
    private var originalText: String = ""
    private val separator = DecimalFormatSymbols.getInstance().decimalSeparator
    private lateinit var view: CheckmarkPopupBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val appComponent = (requireActivity().application as HabitsApplication).component
        val prefs = appComponent.preferences
        view = CheckmarkPopupBinding.inflate(LayoutInflater.from(context))
        arrayOf(view.yesBtn).forEach {
            it.setTextColor(requireArguments().getInt("color"))
        }
        arrayOf(view.noBtn, view.unknownBtnNumber).forEach {
            it.setTextColor(view.root.sres.getColor(R.attr.contrast60))
        }
        arrayOf(view.yesBtn, view.noBtn, view.unknownBtnNumber).forEach {
            it.typeface = InterfaceUtils.getFontAwesome(requireContext())
        }
        if (!prefs.isSkipEnabled) view.skipBtnNumber.visibility = View.GONE
        if (!prefs.areQuestionMarksEnabled) view.unknownBtnNumber.visibility = View.GONE
        view.numberButtons.visibility = View.VISIBLE
        fixDecimalSeparator(view)
        originalNotes = requireArguments().getString("notes")!!
        originalText = requireArguments().getString("text")!!.replace('.', separator)
        view.notes.setText(originalNotes)
        view.value.setText(originalText)
        if (requireArguments().getBoolean("skipped")) view.value.setHint(R.string.skipped)
        view.value.setOnKeyListener { _, keyCode, event ->
            if (event.action == MotionEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                save()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }
        view.saveBtn.setOnClickListener {
            save()
        }
        view.skipBtnNumber.setOnClickListener {
            save(NumberInput.Skip)
        }

        view.unknownBtnNumber.setOnClickListener {
            save(NumberInput.Unknown)
        }

        view.notes.setOnEditorActionListener { v, actionId, event ->
            save()
            true
        }
        view.value.requestFocusWithKeyboard()
        val dialog = Dialog(requireContext())
        dialog.setContentView(view.root)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
        }
        return dialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        if (!dismissedViaSaveAction) {
            val currentNotes = view.notes.text.toString().trim()
            if (currentNotes != originalNotes) {
                onToggle(typed(originalText), currentNotes)
            }
        }
        onDismiss()
    }

    private fun fixDecimalSeparator(view: CheckmarkPopupBinding) {
        // https://stackoverflow.com/a/34256139
        view.value.keyListener = DigitsKeyListener.getInstance("0123456789$separator")

        // https://github.com/flutter/flutter/issues/61175
        val currKeyboard = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        )
        if (currKeyboard.contains("swiftkey") || currKeyboard.contains("samsung")) {
            view.value.inputType = EditorInfo.TYPE_CLASS_TEXT
        }
    }

    fun save(input: NumberInput = typed(view.value.text.toString())) {
        dismissedViaSaveAction = true
        val notes = view.notes.text.toString().trim()
        onToggle(input, notes)
        requireDialog().dismiss()
    }

    private fun typed(text: String) = NumberInput.Typed(text.replace(separator, '.'))
}
