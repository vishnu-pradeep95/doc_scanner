/**
 * TextInputDialogFragment.kt - Dialog for adding text annotations
 * 
 * Allows users to input text with customizable size and color.
 */

package com.pdfscanner.app.editor

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pdfscanner.app.R
import com.pdfscanner.app.databinding.DialogTextInputBinding

/**
 * Dialog for adding text annotations with customization options
 */
class TextInputDialogFragment : DialogFragment() {
    
    private var _binding: DialogTextInputBinding? = null
    private val binding get() = _binding!!
    
    private var selectedColor: Int = Color.BLACK
    private var textSize: Float = 24f
    
    private var onTextConfirmed: ((String, Int, Float) -> Unit)? = null
    
    // Color options
    private val colors = mapOf(
        R.id.colorBlack to Color.parseColor("#000000"),
        R.id.colorRed to Color.parseColor("#E74C3C"),
        R.id.colorBlue to Color.parseColor("#3498DB"),
        R.id.colorGreen to Color.parseColor("#27AE60"),
        R.id.colorPurple to Color.parseColor("#8E44AD"),
        R.id.colorOrange to Color.parseColor("#F39C12")
    )
    
    fun setOnTextConfirmedListener(listener: (String, Int, Float) -> Unit) {
        onTextConfirmed = listener
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogTextInputBinding.inflate(layoutInflater)
        
        setupTextInput()
        setupSizeSlider()
        setupColorPicker()
        setupButtons()
        updatePreview()
        
        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }
    
    private fun setupTextInput() {
        binding.etText.addTextChangedListener {
            updatePreview()
        }
    }
    
    private fun setupSizeSlider() {
        binding.sliderTextSize.addOnChangeListener { _, value, _ ->
            textSize = value
            binding.tvSizeValue.text = value.toInt().toString()
            updatePreview()
        }
    }
    
    private fun setupColorPicker() {
        val colorViews = listOf(
            binding.colorBlack,
            binding.colorRed,
            binding.colorBlue,
            binding.colorGreen,
            binding.colorPurple,
            binding.colorOrange
        )
        
        colorViews.forEach { view ->
            view.setOnClickListener {
                selectColor(view.id)
                updateColorSelection(colorViews, view)
            }
        }
        
        // Select black by default
        updateColorSelection(colorViews, binding.colorBlack)
    }
    
    private fun selectColor(viewId: Int) {
        selectedColor = colors[viewId] ?: Color.BLACK
        updatePreview()
    }
    
    private fun updateColorSelection(allViews: List<View>, selectedView: View) {
        allViews.forEach { view ->
            // For MaterialCardView, use strokeWidth to indicate selection
            if (view is com.google.android.material.card.MaterialCardView) {
                if (view == selectedView) {
                    view.strokeWidth = 6
                    view.strokeColor = Color.parseColor("#4ECDC4")
                } else {
                    view.strokeWidth = 0
                }
            } else {
                val drawable = view.background as? GradientDrawable
                if (view == selectedView) {
                    drawable?.setStroke(6, Color.parseColor("#4ECDC4"))
                } else {
                    drawable?.setStroke(3, Color.TRANSPARENT)
                }
            }
        }
    }
    
    private fun updatePreview() {
        val text = binding.etText.text?.toString() ?: "Sample Text"
        binding.tvPreview.text = text.ifEmpty { "Sample Text" }
        binding.tvPreview.textSize = textSize
        binding.tvPreview.setTextColor(selectedColor)
    }
    
    private fun setupButtons() {
        binding.btnCancel.setOnClickListener {
            dismiss()
        }
        
        binding.btnInsert.setOnClickListener {
            val text = binding.etText.text?.toString()
            if (!text.isNullOrBlank()) {
                onTextConfirmed?.invoke(text, selectedColor, textSize)
                dismiss()
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        const val TAG = "TextInputDialog"
        
        fun newInstance(): TextInputDialogFragment {
            return TextInputDialogFragment()
        }
    }
}
