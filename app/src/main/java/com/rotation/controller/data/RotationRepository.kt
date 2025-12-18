package com.rotation.controller.data

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.rotation.controller.R
import com.rotation.controller.domain.RotationMode

class RotationRepository(private val context: Context) {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(context.getString(R.string.service_enabled_key), true)
        set(value) = prefs.edit().putBoolean(context.getString(R.string.service_enabled_key), value).apply()

    var isGuardEnabled: Boolean
        get() = prefs.getBoolean(context.getString(R.string.guard_key), true)
        set(value) = prefs.edit().putBoolean(context.getString(R.string.guard_key), value).apply()

    var activeMode: RotationMode
        get() {
            val name = prefs.getString(context.getString(R.string.mode_key), null)
            return try {
                if (name != null) RotationMode.valueOf(name) else RotationMode.AUTO
            } catch (e: IllegalArgumentException) {
                RotationMode.AUTO
            }
        }
        set(mode) = prefs.edit().putString(context.getString(R.string.mode_key), mode.name).apply()

    var previousMode: RotationMode?
        get() {
            val name = prefs.getString("previous_mode_key", null)
            return if (name != null) try {
                RotationMode.valueOf(name)
            } catch (e: IllegalArgumentException) {
                null
            } else null
        }
        set(mode) {
            if (mode == null) {
                prefs.edit().remove("previous_mode_key").apply()
            } else {
                prefs.edit().putString("previous_mode_key", mode.name).apply()
            }
        }

    // Auto Lock Settings
    val autoLockWaitSeconds: Int
        get() = prefs.getString(context.getString(R.string.auto_lock_key), "0")?.toIntOrNull() ?: 0

    val isAutoLockEnabled: Boolean
        get() = autoLockWaitSeconds > 0

    val isAutoLockForce: Boolean
        get() = prefs.getBoolean(context.getString(R.string.auto_lock_force_key), false)

    val autoLockMode: RotationMode
        get() {
            val name = prefs.getString(context.getString(R.string.auto_lock_mode_key), null)
            return try {
                if (name != null) RotationMode.valueOf(name) else RotationMode.AUTO
            } catch (e: IllegalArgumentException) {
                RotationMode.AUTO
            }
        }

    // System State Backup
    var savedAccelRotation: Int
        get() = prefs.getInt(context.getString(R.string.saved_accel_rotation_key), -1)
        set(value) = prefs.edit().putInt(context.getString(R.string.saved_accel_rotation_key), value).apply()

    var savedUserRotation: Int
        get() = prefs.getInt(context.getString(R.string.saved_user_rotation_key), -1)
        set(value) = prefs.edit().putInt(context.getString(R.string.saved_user_rotation_key), value).apply()

    fun getShowNotification(): Boolean {
        return prefs.getBoolean(context.getString(R.string.show_notification_key), true)
    }

    fun getEnabledButtons(): Set<String>? {
        return prefs.getStringSet(context.getString(R.string.buttons_key), null)
    }

    fun getRefreshModeDelay(): Long {
         val raw = prefs.getString(context.getString(R.string.refresh_mode_delay_key), "600")
         return raw?.toLongOrNull() ?: 600L
    }
}
