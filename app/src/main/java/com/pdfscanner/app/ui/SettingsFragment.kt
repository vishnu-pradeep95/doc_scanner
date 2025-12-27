/**
 * SettingsFragment.kt - App Settings Screen
 * 
 * Allows users to configure:
 * - Dark mode / theme
 * - Default filter for new scans
 * - About info
 */

package com.pdfscanner.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pdfscanner.app.R
import com.pdfscanner.app.databinding.FragmentSettingsBinding
import com.pdfscanner.app.util.AppPreferences

/**
 * SettingsFragment - App configuration screen
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: AppPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = AppPreferences(requireContext())

        setupToolbar()
        setupSettings()
        updateUI()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupSettings() {
        // Dark mode setting
        binding.settingDarkMode.setOnClickListener {
            showThemeDialog()
        }

        // Default filter setting
        binding.settingDefaultFilter.setOnClickListener {
            showFilterDialog()
        }

        // Share app
        binding.settingShareApp.setOnClickListener {
            shareApp()
        }

        // Version info - get from package manager
        val versionName = try {
            requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0).versionName
        } catch (e: Exception) {
            "1.0.0"
        }
        binding.textVersion.text = getString(R.string.version, versionName)
    }

    private fun updateUI() {
        // Update theme text
        binding.textCurrentTheme.text = when (prefs.getThemeMode()) {
            AppCompatDelegate.MODE_NIGHT_NO -> getString(R.string.theme_light)
            AppCompatDelegate.MODE_NIGHT_YES -> getString(R.string.theme_dark)
            else -> getString(R.string.theme_system)
        }

        // Update filter text
        binding.textCurrentFilter.text = prefs.getDefaultFilterName()
    }

    private fun showThemeDialog() {
        val themes = arrayOf(
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
            getString(R.string.theme_system)
        )
        
        val currentSelection = when (prefs.getThemeMode()) {
            AppCompatDelegate.MODE_NIGHT_NO -> 0
            AppCompatDelegate.MODE_NIGHT_YES -> 1
            else -> 2
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dark_mode)
            .setSingleChoiceItems(themes, currentSelection) { dialog, which ->
                val mode = when (which) {
                    0 -> AppCompatDelegate.MODE_NIGHT_NO
                    1 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                prefs.setThemeMode(mode)
                AppCompatDelegate.setDefaultNightMode(mode)
                updateUI()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showFilterDialog() {
        val filters = arrayOf(
            getString(R.string.filter_original),
            getString(R.string.filter_enhanced),
            getString(R.string.filter_bw),
            getString(R.string.filter_magic),
            getString(R.string.filter_sharpen)
        )
        
        val currentSelection = prefs.getDefaultFilterIndex()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.default_filter)
            .setSingleChoiceItems(filters, currentSelection) { dialog, which ->
                prefs.setDefaultFilter(which)
                updateUI()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun shareApp() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
            putExtra(Intent.EXTRA_TEXT, "Check out PDF Scanner - a simple and powerful document scanner app!")
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_app)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
