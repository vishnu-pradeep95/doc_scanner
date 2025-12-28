/**
 * SignatureDialogFragment.kt - Dialog for capturing signatures
 * 
 * Allows users to draw new signatures or select from saved ones.
 */

package com.pdfscanner.app.editor

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pdfscanner.app.R
import com.pdfscanner.app.databinding.DialogSignatureBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dialog for drawing or selecting signatures
 */
class SignatureDialogFragment : DialogFragment() {
    
    private var _binding: DialogSignatureBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: PdfEditorViewModel by activityViewModels()
    
    private var onSignatureSelected: ((Bitmap) -> Unit)? = null
    
    private lateinit var savedSignaturesAdapter: SavedSignaturesAdapter
    
    fun setOnSignatureSelectedListener(listener: (Bitmap) -> Unit) {
        onSignatureSelected = listener
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogSignatureBinding.inflate(layoutInflater)
        
        setupSignaturePad()
        setupSavedSignatures()
        setupButtons()
        
        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }
    
    private fun setupSignaturePad() {
        binding.signaturePad.onSignatureChanged = { hasSignature ->
            binding.tvSignHere.isVisible = !hasSignature
            binding.btnInsert.isEnabled = hasSignature
        }
    }
    
    private fun setupSavedSignatures() {
        savedSignaturesAdapter = SavedSignaturesAdapter(
            onSelect = { signature ->
                lifecycleScope.launch {
                    val bitmap = viewModel.loadSignatureBitmap(signature)
                    if (bitmap != null) {
                        onSignatureSelected?.invoke(bitmap)
                        dismiss()
                    }
                }
            },
            onDelete = { signature ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Signature")
                    .setMessage("Are you sure you want to delete this saved signature?")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deleteSignature(signature)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        
        binding.rvSavedSignatures.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = savedSignaturesAdapter
        }
        
        viewModel.savedSignatures.observe(this) { signatures ->
            savedSignaturesAdapter.submitList(signatures)
            binding.rvSavedSignatures.isVisible = signatures.isNotEmpty()
            binding.tvEmptySignatures.isVisible = signatures.isEmpty()
        }
    }
    
    private fun setupButtons() {
        binding.btnClear.setOnClickListener {
            binding.signaturePad.clear()
        }
        
        binding.btnUndo.setOnClickListener {
            binding.signaturePad.undo()
        }
        
        binding.cbSaveSignature.setOnCheckedChangeListener { _, isChecked ->
            binding.tilSignatureName.isVisible = isChecked
        }
        
        binding.btnCancel.setOnClickListener {
            dismiss()
        }
        
        binding.btnInsert.setOnClickListener {
            val bitmap = binding.signaturePad.getCroppedSignatureBitmap()
            if (bitmap != null) {
                // Save if checkbox is checked
                if (binding.cbSaveSignature.isChecked) {
                    val name = binding.etSignatureName.text?.toString()?.takeIf { it.isNotBlank() }
                        ?: "Signature ${System.currentTimeMillis()}"
                    viewModel.saveSignature(bitmap, name)
                }
                
                onSignatureSelected?.invoke(bitmap)
                dismiss()
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        const val TAG = "SignatureDialog"
        
        fun newInstance(): SignatureDialogFragment {
            return SignatureDialogFragment()
        }
    }
}

/**
 * Adapter for saved signatures list
 */
class SavedSignaturesAdapter(
    private val onSelect: (PdfEditorViewModel.SavedSignature) -> Unit,
    private val onDelete: (PdfEditorViewModel.SavedSignature) -> Unit
) : RecyclerView.Adapter<SavedSignaturesAdapter.ViewHolder>() {
    
    private var signatures = listOf<PdfEditorViewModel.SavedSignature>()
    
    fun submitList(list: List<PdfEditorViewModel.SavedSignature>) {
        signatures = list
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_saved_signature, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(signatures[position])
    }
    
    override fun getItemCount() = signatures.size
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivSignature: ImageView = itemView.findViewById(R.id.ivSignature)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)
        
        fun bind(signature: PdfEditorViewModel.SavedSignature) {
            // Load signature bitmap in background
            itemView.post {
                val bitmap = BitmapFactory.decodeFile(signature.filePath)
                ivSignature.setImageBitmap(bitmap)
            }
            
            itemView.setOnClickListener { onSelect(signature) }
            btnDelete.setOnClickListener { onDelete(signature) }
        }
    }
}
