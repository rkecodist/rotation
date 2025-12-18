package com.rotation.controller.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.rotation.controller.R
import com.rotation.controller.core.RotationController
import com.rotation.controller.core.RotationControllerImpl
import com.rotation.controller.data.RotationRepository
import com.rotation.controller.domain.RotationMode

class RotationControlService : Service() {

    companion object {
        const val TAG = "RotationControlService"
        const val ACTION_START = "START"
        const val ACTION_CONFIGURATION_CHANGED = "CONFIGURATION_CHANGED"
        const val ACTION_ORIENTATION_CHANGED = "ORIENTATION_CHANGED"
        const val ACTION_PRESETS_UPDATE = "PRESETS_UPDATE"
        const val ACTION_PRESETS_RESTORE = "PRESETS_RESTORE"

        const val ACTION_REFRESH_NOTIFICATION = "REFRESH"
        const val ACTION_REFRESH_NOTIFICATION_REQUEST_CODE = 10

        const val ACTION_CHANGE_GUARD = "CHANGE_GUARD"
        const val ACTION_CHANGE_GUARD_REQUEST_CODE = 20

        const val ACTION_CHANGE_MODE = "CHANGE_MODE"
        const val ACTION_CHANGE_MODE_REQUEST_CODE_BASE = 30
        const val INTENT_NEW_MODE = "NEW_MODE"

        const val ACTION_REFRESH_MODE = "REFRESH_MODE"
        const val ACTION_REFRESH_MODE_REQUEST_CODE = 40

        const val ACTION_TOGGLE_SERVICE = "TOGGLE_SERVICE"
        const val ACTION_TOGGLE_SERVICE_REQUEST_CODE = 50

        const val ACTION_EXIT_SERVICE = "EXIT_SERVICE"
        const val ACTION_EXIT_SERVICE_REQUEST_CODE = 60

        const val ACTION_NOTIFY_CREATED = "com.rotation.controller.SERVICE_CREATED"
        const val ACTION_NOTIFY_DESTROYED = "com.rotation.controller.SERVICE_DESTROYED"
        const val ACTION_NOTIFY_UPDATED = "com.rotation.controller.SERVICE_UPDATED"

        fun isRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if (RotationControlService::class.java.name == service.service.className) {
                    return true
                }
            }
            return false
        }

        fun start(context: Context) {
            val intent = Intent(context, RotationControlService::class.java)
            intent.action = ACTION_START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, RotationControlService::class.java)
            context.stopService(intent)
        }
    }

    private lateinit var repository: RotationRepository
    private lateinit var controller: RotationController
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var handler: Handler

    private var currentlyRefreshing = false
    private var lastDisplayRotationValue = -1

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
               val refreshOnUnlock = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                   .getBoolean(context.getString(R.string.refresh_on_unlock_key), false)

               if (refreshOnUnlock) {
                   startService(Intent(context, RotationControlService::class.java).apply {
                       action = ACTION_REFRESH_MODE
                   })
               }
            }
        }
    }

    private val orientationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
             if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) {
                 setupAutoLock()
             }
        }
    }

    private val chargeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
             intent?.let { handleChargeIntent(context, it) }
        }
    }

    private val broadcastToggleGuardIntent = Runnable {
        currentlyRefreshing = false
        Log.i(TAG, "Guard restore after refresh")
        updateServiceState()
    }

    private val triggerAutoLock = Runnable {
        Log.i(TAG, "Triggering auto lock")
        performAutoLock()
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")

        repository = RotationRepository(this)
        controller = RotationControllerImpl(this)
        notificationHelper = NotificationHelper(this)
        handler = Handler(Looper.getMainLooper())

        if (repository.isServiceEnabled) {
            saveSystemState()
        }

        registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
        registerReceiver(orientationReceiver, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))

        val chargeFilter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(chargeReceiver, chargeFilter)

        setupAutoLock()
        sendBroadcast(Intent(ACTION_NOTIFY_CREATED))
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        controller.reset()

        try {
            unregisterReceiver(unlockReceiver)
            unregisterReceiver(orientationReceiver)
            unregisterReceiver(chargeReceiver)
        } catch (e: Exception) {
            // Ignore if not registered
        }

        sendBroadcast(Intent(ACTION_NOTIFY_DESTROYED))

        notificationHelper.cancel(NotificationHelper.NOTIFICATION_ID)

        handler.removeCallbacks(broadcastToggleGuardIntent)
        handler.removeCallbacks(triggerAutoLock)

        restoreSystemState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val action = intent.action
        Log.i(TAG, "onStartCommand action=$action")

        when (action) {
            ACTION_START -> {
                val notification = notificationHelper.createNotification(
                    repository.getShowNotification(),
                    repository.activeMode,
                    repository.isGuardEnabled,
                    repository.isServiceEnabled,
                    repository
                )

                if (Build.VERSION.SDK_INT >= 34) { // UPSIDE_DOWN_CAKE
                    startForeground(NotificationHelper.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NotificationHelper.NOTIFICATION_ID, notification)
                }
            }
            ACTION_CONFIGURATION_CHANGED, ACTION_ORIENTATION_CHANGED -> {
                setupAutoLock()
            }
            ACTION_PRESETS_UPDATE -> {
                val modeName = intent.getStringExtra(INTENT_NEW_MODE)
                if (modeName != null) {
                    val newMode = RotationMode.valueOf(modeName)
                    repository.previousMode = repository.activeMode
                    repository.activeMode = newMode

                    if (isCharging()) {
                         androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                            .edit()
                            .putString(getString(R.string.saved_original_mode_for_charge_key), repository.activeMode.name)
                            .apply()
                    }
                }
            }
            ACTION_PRESETS_RESTORE -> {
                repository.previousMode?.let {
                    repository.activeMode = it
                    repository.previousMode = null
                }
            }
            ACTION_REFRESH_NOTIFICATION -> {
                Log.i(TAG, "Refresh notification")
            }
            ACTION_CHANGE_GUARD -> {
                repository.isGuardEnabled = !repository.isGuardEnabled
            }
            ACTION_CHANGE_MODE -> {
                val modeName = intent.getStringExtra(INTENT_NEW_MODE)
                if (modeName != null) {
                    val newMode = RotationMode.valueOf(modeName)
                    repository.activeMode = newMode
                    repository.previousMode = null

                    if (isCharging()) {
                         androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                            .edit()
                            .putString(getString(R.string.saved_original_mode_for_charge_key), repository.activeMode.name)
                            .apply()
                    }
                }
            }
            ACTION_REFRESH_MODE -> {
                currentlyRefreshing = true
                val delay = repository.getRefreshModeDelay()
                handler.postDelayed(broadcastToggleGuardIntent, delay)
            }
            ACTION_TOGGLE_SERVICE -> {
                val wasEnabled = repository.isServiceEnabled
                repository.isServiceEnabled = !wasEnabled
                if (repository.isServiceEnabled && !wasEnabled) {
                    saveSystemState()
                } else if (!repository.isServiceEnabled && wasEnabled) {
                    restoreSystemState()
                }
            }
            ACTION_EXIT_SERVICE -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        updateServiceState()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateServiceState() {
        val activeMode = repository.activeMode
        val isGuard = repository.isGuardEnabled
        val isEnabled = repository.isServiceEnabled

        if (isEnabled) {
            controller.applyMode(activeMode, (isGuard || activeMode.requiresGuard) && !currentlyRefreshing)
        } else {
            // Restore happens in ACTION_TOGGLE_SERVICE or onDestroy, but ensure overlay is gone
            controller.reset()
        }

        if (repository.getShowNotification()) {
            val notification = notificationHelper.createNotification(
                true, activeMode, isGuard, isEnabled, repository
            )
            notificationHelper.notify(NotificationHelper.NOTIFICATION_ID, notification)
        } else {
            notificationHelper.cancel(NotificationHelper.NOTIFICATION_ID)
        }

        sendBroadcast(Intent(ACTION_NOTIFY_UPDATED))
    }

    private fun saveSystemState() {
        try {
            val accelRotation = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION)
            val userRotation = Settings.System.getInt(contentResolver, Settings.System.USER_ROTATION, 0)
            repository.savedAccelRotation = accelRotation
            repository.savedUserRotation = userRotation
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save system state", e)
        }
    }

    private fun restoreSystemState() {
        val accelRotation = repository.savedAccelRotation
        val userRotation = repository.savedUserRotation

        if (accelRotation != -1) {
            Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, accelRotation)
        }
        if (userRotation != -1) {
            Settings.System.putInt(contentResolver, Settings.System.USER_ROTATION, userRotation)
        }
    }

    private fun setupAutoLock() {
        handler.removeCallbacks(triggerAutoLock)
        if (!repository.isAutoLockEnabled || currentlyRefreshing) return
        if (repository.activeMode != RotationMode.AUTO) return

        lastDisplayRotationValue = (getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
        handler.postDelayed(triggerAutoLock, repository.autoLockWaitSeconds * 1000L)
    }

    private fun performAutoLock() {
        if (lastDisplayRotationValue == -1) {
             lastDisplayRotationValue = (getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
        }

        var newMode = repository.autoLockMode
        if (newMode == RotationMode.AUTO) {
            newMode = RotationMode.fromRotationValue(lastDisplayRotationValue)
        } else if (!repository.isAutoLockForce) {
             val currentMode = RotationMode.fromRotationValue((getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation)
             if (!isCompatible(newMode, currentMode)) return
        }

        Toast.makeText(this, getString(R.string.auto_lock_trigger, getString(newMode.stringId)), Toast.LENGTH_SHORT).show()
        repository.activeMode = newMode

        // Notify change
        startService(Intent(this, RotationControlService::class.java).apply {
            action = ACTION_CONFIGURATION_CHANGED
        })
    }

    private fun isCompatible(toMode: RotationMode, currentMode: RotationMode): Boolean {
        if (toMode == currentMode) return true
        if (toMode == RotationMode.LANDSCAPE_SENSOR && (currentMode == RotationMode.LANDSCAPE || currentMode == RotationMode.LANDSCAPE_REVERSE)) return true
        if (toMode == RotationMode.PORTRAIT_SENSOR && (currentMode == RotationMode.PORTRAIT || currentMode == RotationMode.PORTRAIT_REVERSE)) return true
        return false
    }

    private fun isCharging(): Boolean {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val plugged = intent.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, -1)
        return plugged == android.os.BatteryManager.BATTERY_PLUGGED_AC ||
               plugged == android.os.BatteryManager.BATTERY_PLUGGED_USB ||
               plugged == android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS
    }

    private fun handleChargeIntent(context: Context, intent: Intent) {
         if (!repository.isServiceEnabled) return
         val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)

         if (intent.action == Intent.ACTION_POWER_CONNECTED) {
             val modeStr = prefs.getString(getString(R.string.smart_charge_connect_mode_key), "NONE")
             if (modeStr == "NONE") return

             prefs.edit().putString(getString(R.string.saved_original_mode_for_charge_key), repository.activeMode.name).apply()
             try {
                 repository.activeMode = RotationMode.valueOf(modeStr!!)
                 updateServiceState()
             } catch (e: Exception) {}
         } else if (intent.action == Intent.ACTION_POWER_DISCONNECTED) {
             val modeStr = prefs.getString(getString(R.string.smart_charge_disconnect_mode_key), "LAST_USED")
             if (modeStr == "NONE") return

             if (modeStr == "LAST_USED") {
                 val originalMode = prefs.getString(getString(R.string.saved_original_mode_for_charge_key), RotationMode.AUTO.name)
                 try {
                     repository.activeMode = RotationMode.valueOf(originalMode!!)
                 } catch (e: Exception) {}
             } else {
                 try {
                     repository.activeMode = RotationMode.valueOf(modeStr!!)
                 } catch (e: Exception) {}
             }
             updateServiceState()
         }
    }
}
