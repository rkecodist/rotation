package com.rotation.controller.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            if (prefs.getBoolean(context.getString(com.rotation.controller.R.string.start_on_boot_key), false)) {
                val serviceIntent = Intent(context, RotationControlService::class.java).apply {
                    action = RotationControlService.ACTION_START
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
