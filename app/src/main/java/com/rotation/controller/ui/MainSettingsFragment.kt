package com.rotation.controller.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.rotation.controller.R
import com.rotation.controller.domain.RotationMode
import com.rotation.controller.service.RotationControlService

class MainSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)

        setupStartControlPreference()
        setupGuardPreference()
        setupModePreference()
        setupServiceEnabledPreference()
        setupAccessibilityPreference()
    }

    private fun setupStartControlPreference() {
        findPreference<Preference>(getString(R.string.start_control_key))?.apply {
            setOnPreferenceClickListener {
                if (RotationControlService.isRunning(requireContext())) {
                    RotationControlService.stop(requireContext())
                } else {
                    RotationControlService.start(requireContext())
                }
                true
            }
        }
    }

    private fun setupGuardPreference() {
        findPreference<SwitchPreferenceCompat>(getString(R.string.guard_key))?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) {
                    if (!Settings.canDrawOverlays(requireContext())) {
                        showOverlayPermissionDialog()
                        false
                    } else {
                        updateService(RotationControlService.ACTION_CHANGE_GUARD)
                        true
                    }
                } else {
                    updateService(RotationControlService.ACTION_CHANGE_GUARD)
                    true
                }
            }
        }
    }

    private fun setupModePreference() {
        findPreference<Preference>(getString(R.string.mode_key))?.apply {
            summary = getString(R.string.mode_description, getString(RotationMode.fromPreferences(requireContext()).stringId))
            setOnPreferenceClickListener {
                showModeSelectionDialog()
                true
            }
        }
    }

    private fun setupServiceEnabledPreference() {
        findPreference<SwitchPreferenceCompat>(getString(R.string.service_enabled_key))?.apply {
             setOnPreferenceChangeListener { _, _ ->
                 updateService(RotationControlService.ACTION_TOGGLE_SERVICE)
                 true
             }
        }
    }

    private fun setupAccessibilityPreference() {
        findPreference<Preference>(getString(R.string.configure_presets_key))?.apply {
             setOnPreferenceClickListener {
                 if (!isAccessibilityServiceEnabled()) {
                      showAccessibilityPermissionDialog()
                 } else {
                      // Navigate to Presets
                      startActivity(Intent(requireContext(), PresetsActivity::class.java))
                 }
                 true
             }
        }
    }

    private fun showOverlayPermissionDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.require_overlay_permission)
            .setMessage(R.string.guard_description)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                startActivity(intent)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showModeSelectionDialog() {
        val modes = RotationMode.entries
        val items = modes.map { getString(it.stringId) }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.mode_title)
            .setItems(items) { _, which ->
                val selectedMode = modes[which]
                findPreference<Preference>(getString(R.string.mode_key))?.summary =
                    getString(R.string.mode_description, getString(selectedMode.stringId))

                // Update Preference directly as Service reads from it or Intent updates it
                // Logic in Service ACTION_CHANGE_MODE updates preference? Yes.
                // But we should probably send intent to service if running.

                val intent = Intent(requireContext(), RotationControlService::class.java).apply {
                    action = RotationControlService.ACTION_CHANGE_MODE
                    putExtra(RotationControlService.INTENT_NEW_MODE, selectedMode.name)
                }

                // Also update preference immediately for UI responsiveness if service not running
                 androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
                        .edit()
                        .putString(getString(R.string.mode_key), selectedMode.name)
                        .apply()

                if (RotationControlService.isRunning(requireContext())) {
                    requireContext().startService(intent)
                }
            }
            .show()
    }

    private fun showAccessibilityPermissionDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.accessibility_permission_required_title)
            .setMessage(R.string.accessibility_permission_required_description)
            .setPositiveButton(R.string.accessibility_permission_required_positive) { _, _ ->
                 startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton(R.string.accessibility_permission_required_negative, null)
            .show()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        // Simplified check
        // Correct way:
        // val expectedComponentName = "${requireContext().packageName}/${com.rotation.controller.RotationAccessibilityService::class.java.canonicalName}"
        // But wait, the class name will change to new package.
        // Let's implement AccessibilityService later in next step properly.
        // For now, I will just assume false or check basic settings string.

        val enabledServices = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(requireContext().packageName) == true
    }

    private fun updateService(action: String) {
        if (RotationControlService.isRunning(requireContext())) {
            val intent = Intent(requireContext(), RotationControlService::class.java).apply {
                this.action = action
            }
            requireContext().startService(intent)
        }
    }
}
