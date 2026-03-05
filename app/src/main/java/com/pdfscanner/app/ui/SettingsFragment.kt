/**
 * SettingsFragment.kt - App Settings Screen
 *
 * Allows users to configure:
 * - Dark mode / theme
 * - Default filter for new scans
 * - App lock (biometric/PIN)
 * - About info
 */

package com.pdfscanner.app.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pdfscanner.app.R
import com.pdfscanner.app.databinding.FragmentSettingsBinding
import com.pdfscanner.app.util.AppLockManager
import com.pdfscanner.app.util.AppPreferences

/**
 * SettingsFragment - App configuration screen
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val duration = resources.getInteger(R.integer.motion_duration_large).toLong()
        enterTransition = com.google.android.material.transition.MaterialFadeThrough().apply { this.duration = duration }
        returnTransition = com.google.android.material.transition.MaterialFadeThrough().apply { this.duration = duration }
    }

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

        // Edge-to-edge inset handling
        // SettingsFragment uses a ScrollView root — apply top inset to toolbar, bottom to root
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = insets.top)
            windowInsets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = insets.bottom)
            windowInsets
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        
        // Setup toolbar menu (home button)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_home -> {
                    // Navigate back to home
                    findNavController().navigate(R.id.action_settings_to_home)
                    true
                }
                else -> false
            }
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

        // App lock toggle
        setupAppLock()

        // Lock timeout
        binding.settingLockTimeout.setOnClickListener {
            showTimeoutDialog()
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

        // Update lock UI
        updateLockUI()
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

    /**
     * Set up the app lock toggle with biometric availability checks.
     * Hides the entire lock row if the device has no authentication hardware.
     */
    private fun setupAppLock() {
        val biometricManager = BiometricManager.from(requireContext())
        val authenticators = AppLockManager.getAllowedAuthenticators()
        val result = biometricManager.canAuthenticate(authenticators)

        // If device has no authentication hardware at all, hide the lock row entirely
        if (result == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ||
            result == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
        ) {
            binding.settingAppLock.visibility = View.GONE
            return
        }

        // Set initial checked state
        binding.switchAppLock.isChecked = AppLockManager.isLockEnabled(requireContext())

        binding.switchAppLock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val canAuth = biometricManager.canAuthenticate(authenticators)
                if (canAuth == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
                    // Revert toggle -- no enrollment on device
                    binding.switchAppLock.isChecked = false
                    showDeviceSecurityRequiredDialog()
                } else if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                    AppLockManager.setLockEnabled(requireContext(), true)
                } else {
                    // Unexpected state -- revert
                    binding.switchAppLock.isChecked = false
                }
            } else {
                AppLockManager.setLockEnabled(requireContext(), false)
                AppLockManager.clearAuthState(requireContext())
            }
            updateLockUI()
        }
    }

    /**
     * Show/hide the timeout row based on lock state, and update displayed timeout value.
     */
    private fun updateLockUI() {
        val lockEnabled = AppLockManager.isLockEnabled(requireContext())
        binding.settingLockTimeout.visibility = if (lockEnabled) View.VISIBLE else View.GONE

        if (lockEnabled) {
            val timeoutText = when (AppLockManager.getTimeout(requireContext())) {
                AppLockManager.TIMEOUT_30_SECONDS -> getString(R.string.timeout_30_seconds)
                AppLockManager.TIMEOUT_1_MINUTE -> getString(R.string.timeout_1_minute)
                AppLockManager.TIMEOUT_5_MINUTES -> getString(R.string.timeout_5_minutes)
                else -> getString(R.string.timeout_immediate)
            }
            binding.textCurrentTimeout.text = timeoutText
        }
    }

    /**
     * Show timeout selection dialog, following the same pattern as showThemeDialog().
     */
    private fun showTimeoutDialog() {
        val timeoutLabels = arrayOf(
            getString(R.string.timeout_immediate),
            getString(R.string.timeout_30_seconds),
            getString(R.string.timeout_1_minute),
            getString(R.string.timeout_5_minutes)
        )
        val timeoutValues = longArrayOf(
            AppLockManager.TIMEOUT_IMMEDIATE,
            AppLockManager.TIMEOUT_30_SECONDS,
            AppLockManager.TIMEOUT_1_MINUTE,
            AppLockManager.TIMEOUT_5_MINUTES
        )
        val currentTimeout = AppLockManager.getTimeout(requireContext())
        val currentIndex = timeoutValues.indexOf(currentTimeout).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.auto_lock_timeout)
            .setSingleChoiceItems(timeoutLabels, currentIndex) { dialog, which ->
                AppLockManager.setTimeout(requireContext(), timeoutValues[which])
                updateLockUI()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Show dialog explaining that device security (PIN/biometric) must be set up first.
     */
    private fun showDeviceSecurityRequiredDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.device_security_required)
            .setMessage(R.string.device_security_required_message)
            .setPositiveButton(R.string.ok) { _, _ ->
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent(Settings.ACTION_BIOMETRIC_ENROLL)
                } else {
                    Intent(Settings.ACTION_SECURITY_SETTINGS)
                }
                startActivity(intent)
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
