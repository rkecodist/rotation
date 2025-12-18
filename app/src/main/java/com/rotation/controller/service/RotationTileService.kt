package com.rotation.controller.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import com.rotation.controller.R
import com.rotation.controller.domain.RotationMode
import com.rotation.controller.data.RotationRepository

@RequiresApi(Build.VERSION_CODES.N)
class RotationTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val repository = RotationRepository(this)

        // Simple toggle behavior for now or show dialog
        // Ideally we check preference for click behavior

        val clickBehavior = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            .getString(getString(R.string.tile_click_behavior_key), "SHOW_MODES_IF_CONTROLLING")

        if (RotationControlService.isRunning(this)) {
             // If running, behavior depends on settings
             if (clickBehavior == "TOGGLE_SERVICE") {
                 startService(Intent(this, RotationControlService::class.java).apply {
                     action = RotationControlService.ACTION_TOGGLE_SERVICE
                 })
             } else {
                 // Show dialog - explicit intents or activity?
                 // TileService can show dialogs
                 showDialog(QuickActionsDialog(this))
             }
        } else {
            // Start service
            val startIntent = Intent(this, RotationControlService::class.java).apply {
                action = RotationControlService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= 34) {
                startForegroundService(startIntent)
            } else {
                startService(startIntent)
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val isRunning = RotationControlService.isRunning(this)

        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_title)

        if (isRunning) {
            val repository = RotationRepository(this)
            val mode = repository.activeMode
            tile.subtitle = getString(mode.stringId)
        } else {
            tile.subtitle = getString(R.string.tile_inactive)
        }

        tile.updateTile()
    }
}
