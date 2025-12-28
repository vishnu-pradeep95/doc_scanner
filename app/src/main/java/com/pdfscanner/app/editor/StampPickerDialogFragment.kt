/**
 * StampPickerDialogFragment.kt - Dialog for selecting stamp types
 */

package com.pdfscanner.app.editor

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pdfscanner.app.R
import com.pdfscanner.app.databinding.DialogStampPickerBinding

/**
 * Dialog for selecting a stamp to add to the PDF
 */
class StampPickerDialogFragment : DialogFragment() {
    
    private var _binding: DialogStampPickerBinding? = null
    private val binding get() = _binding!!
    
    private var onStampSelected: ((StampType) -> Unit)? = null
    
    fun setOnStampSelectedListener(listener: (StampType) -> Unit) {
        onStampSelected = listener
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogStampPickerBinding.inflate(layoutInflater)
        
        setupRecyclerView()
        setupButtons()
        
        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }
    
    private fun setupRecyclerView() {
        val stamps = listOf(
            StampInfo(StampType.APPROVED, "APPROVED", Color.parseColor("#27AE60")),
            StampInfo(StampType.REJECTED, "REJECTED", Color.parseColor("#E74C3C")),
            StampInfo(StampType.DRAFT, "DRAFT", Color.parseColor("#F39C12")),
            StampInfo(StampType.CONFIDENTIAL, "CONFIDENTIAL", Color.parseColor("#8E44AD")),
            StampInfo(StampType.COPY, "COPY", Color.parseColor("#3498DB")),
            StampInfo(StampType.FINAL, "FINAL", Color.parseColor("#1ABC9C")),
            StampInfo(StampType.VOID, "VOID", Color.parseColor("#7F8C8D")),
            StampInfo(StampType.PAID, "PAID", Color.parseColor("#27AE60")),
            StampInfo(StampType.RECEIVED, "RECEIVED", Color.parseColor("#2980B9"))
        )
        
        binding.rvStamps.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = StampAdapter(stamps) { stamp ->
                onStampSelected?.invoke(stamp.type)
                dismiss()
            }
        }
    }
    
    private fun setupButtons() {
        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        const val TAG = "StampPickerDialog"
        
        fun newInstance(): StampPickerDialogFragment {
            return StampPickerDialogFragment()
        }
    }
}

/**
 * Data class for stamp information
 */
data class StampInfo(
    val type: StampType,
    val text: String,
    val color: Int
)

/**
 * Adapter for stamp selection grid
 */
class StampAdapter(
    private val stamps: List<StampInfo>,
    private val onStampClick: (StampInfo) -> Unit
) : RecyclerView.Adapter<StampAdapter.ViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stamp, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(stamps[position])
    }
    
    override fun getItemCount() = stamps.size
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: CardView = itemView as CardView
        private val tvStampText: TextView = itemView.findViewById(R.id.tvStampText)
        
        fun bind(stamp: StampInfo) {
            tvStampText.text = stamp.text
            card.setCardBackgroundColor(stamp.color)
            
            itemView.setOnClickListener {
                onStampClick(stamp)
            }
        }
    }
}
