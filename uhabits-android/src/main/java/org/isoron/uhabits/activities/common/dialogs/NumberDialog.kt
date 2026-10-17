package org.isoron.uhabits.activities.common.dialogs

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.method.NumberKeyListener
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatDialogFragment
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.R
import org.isoron.uhabits.core.ui.NumberField
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
        acceptEitherSeparator(view)
        originalNotes = requireArguments().getString(ARG_NOTES)!!
        originalText = requireArguments().getString(ARG_TEXT)!!.replace('.', separator)
        view.notes.setText(originalNotes)
        view.value.setText(originalText)
        if (requireArguments().getBoolean(ARG_SKIPPED)) view.value.setHint(R.string.skipped)
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
                onToggle(NumberInput.Typed(originalText), currentNotes)
            }
        }
        onDismiss()
    }

    private fun acceptEitherSeparator(view: CheckmarkPopupBinding) {
        // Keyboards differ in which separator they offer, and in landscape the
        // fullscreen editor commits text without key events, so a filter on
        // the whole text decides; the listener keeps the numeric keypad.
        view.value.keyListener = object : NumberKeyListener() {
            override fun getInputType() =
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            override fun getAcceptedChars() = "0123456789.,".toCharArray()
        }
        view.value.filters = arrayOf(
            InputFilter { source, start, end, dest, dstart, dend ->
                val text = dest.replaceRange(dstart, dend, source.subSequence(start, end))
                if (NumberField.accepts(text.toString())) null else dest.subSequence(dstart, dend)
            }
        )
    }

    fun save(input: NumberInput = NumberInput.Typed(view.value.text.toString())) {
        dismissedViaSaveAction = true
        val notes = view.notes.text.toString().trim()
        onToggle(input, notes)
        requireDialog().dismiss()
    }

    companion object {
        private const val ARG_TEXT = "text"
        private const val ARG_SKIPPED = "skipped"
        private const val ARG_NOTES = "notes"

        fun newInstance(field: NumberField, notes: String) = NumberDialog().apply {
            arguments = Bundle().apply {
                putString(ARG_TEXT, field.text)
                putBoolean(ARG_SKIPPED, field.isSkipped)
                putString(ARG_NOTES, notes)
            }
        }
    }
}
