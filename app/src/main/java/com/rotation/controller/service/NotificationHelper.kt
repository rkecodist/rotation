package com.rotation.controller.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import com.rotation.controller.R
import com.rotation.controller.data.RotationRepository
import com.rotation.controller.domain.RotationMode

class NotificationHelper(private val context: Context) {

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CONTROLS_CHANNEL_ID = "Controls"
        const val SERVICE_CHANNEL_ID = "Service"
        const val WARNING_CHANNEL_ID = "Warning"

        const val NOTIFICATION_ID = 1
        const val PRESETS_NOTIFICATION_ID = 2

        const val TINT_METHOD = "setColorFilter"
    }

    init {
        createNotificationChannel(CONTROLS_CHANNEL_ID, R.string.controls_notification_channel_name)
        createNotificationChannel(SERVICE_CHANNEL_ID, R.string.service_notification_channel_name)
        createNotificationChannel(WARNING_CHANNEL_ID, R.string.warning_notification_channel_name)
    }

    private fun createNotificationChannel(id: String, @StringRes name: Int) {
        val channel = NotificationChannel(id, context.getString(name), NotificationManager.IMPORTANCE_DEFAULT).apply {
            setSound(null, null)
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_SECRET
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun createNotification(
        isNotificationShown: Boolean,
        activeMode: RotationMode,
        isGuardEnabled: Boolean,
        isServiceEnabled: Boolean,
        repository: RotationRepository
    ): Notification {
        val channelId = if (isNotificationShown) CONTROLS_CHANNEL_ID else SERVICE_CHANNEL_ID

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.mode_auto)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)

        if (isNotificationShown) {
            val layout = RemoteViews(context.packageName, R.layout.notification)
            setupPendingIntents(layout)
            updateViews(layout, activeMode, isGuardEnabled, isServiceEnabled, repository)

            builder.setCustomContentView(layout)
                .setCustomBigContentView(layout)
                .setDeleteIntent(newRefreshPendingIntent())
                .setSubText(null)
        } else {
            builder.setSubText(context.getString(R.string.notification_discard_me_title))
                .setCustomContentView(null)
                .setCustomBigContentView(null)
                .setDeleteIntent(null)
        }

        return builder.build()
    }

    private fun setupPendingIntents(layout: RemoteViews) {
        layout.setOnClickPendingIntent(R.id.guard, newGuardPendingIntent())
        layout.setOnClickPendingIntent(R.id.toggle_service, newToggleServicePendingIntent())
        layout.setOnClickPendingIntent(R.id.exit_service, newExitServicePendingIntent())
        layout.setOnClickPendingIntent(R.id.refresh, newRefreshPendingIntent())

        RotationMode.entries.forEach { mode ->
            layout.setOnClickPendingIntent(mode.viewId, newModePendingIntent(mode))
        }
    }

    private fun updateViews(
        layout: RemoteViews,
        activeMode: RotationMode,
        isGuardEnabled: Boolean,
        isServiceEnabled: Boolean,
        repository: RotationRepository
    ) {
        val enabledButtons = repository.getEnabledButtons()

        RotationMode.entries.forEach { mode ->
            if (enabledButtons != null && !enabledButtons.contains(mode.name)) {
                layout.setViewVisibility(mode.viewId, View.GONE)
            } else {
                layout.setViewVisibility(mode.viewId, View.VISIBLE)
            }
            layout.setInt(mode.viewId, TINT_METHOD, context.getColor(R.color.inactive))
        }

        fun updateButtonVisibility(buttonKey: String, viewId: Int) {
            if (enabledButtons != null && !enabledButtons.contains(buttonKey)) {
                layout.setViewVisibility(viewId, View.GONE)
            } else {
                layout.setViewVisibility(viewId, View.VISIBLE)
            }
        }

        updateButtonVisibility("POWER", R.id.toggle_service)
        updateButtonVisibility("EXIT", R.id.exit_service)
        updateButtonVisibility("GUARD", R.id.guard)
        updateButtonVisibility("REFRESH", R.id.refresh)

        if (isServiceEnabled) {
            layout.setInt(activeMode.viewId, TINT_METHOD, context.getColor(R.color.active))
            layout.setInt(R.id.toggle_service, TINT_METHOD, context.getColor(R.color.active))
            layout.setInt(R.id.exit_service, TINT_METHOD, context.getColor(R.color.inactive))
            layout.setInt(R.id.refresh, TINT_METHOD, context.getColor(R.color.inactive))

            val guardColor = if (isGuardEnabled || activeMode.requiresGuard) R.color.active else R.color.inactive
            layout.setInt(R.id.guard, TINT_METHOD, context.getColor(guardColor))
        } else {
             listOf(R.id.toggle_service, R.id.guard, R.id.exit_service, R.id.refresh).forEach {
                 layout.setInt(it, TINT_METHOD, context.getColor(R.color.inactive))
             }
        }
    }

    fun notify(id: Int, notification: Notification) {
        notificationManager.notify(id, notification)
    }

    fun cancel(id: Int) {
        notificationManager.cancel(id)
    }

    private fun newRefreshPendingIntent(): PendingIntent {
        val intent = Intent(context, RotationControlService::class.java).apply {
            action = RotationControlService.ACTION_REFRESH_NOTIFICATION
        }
        return PendingIntent.getService(
            context,
            RotationControlService.ACTION_REFRESH_NOTIFICATION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun newToggleServicePendingIntent(): PendingIntent {
        val intent = Intent(context, RotationControlService::class.java).apply {
            action = RotationControlService.ACTION_TOGGLE_SERVICE
        }
        return PendingIntent.getService(
            context,
            RotationControlService.ACTION_TOGGLE_SERVICE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun newExitServicePendingIntent(): PendingIntent {
        val intent = Intent(context, RotationControlService::class.java).apply {
            action = RotationControlService.ACTION_EXIT_SERVICE
        }
        return PendingIntent.getService(
            context,
            RotationControlService.ACTION_EXIT_SERVICE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun newGuardPendingIntent(): PendingIntent {
        val intent = Intent(context, RotationControlService::class.java).apply {
            action = RotationControlService.ACTION_CHANGE_GUARD
        }
        return PendingIntent.getService(
            context,
            RotationControlService.ACTION_CHANGE_GUARD_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun newModePendingIntent(mode: RotationMode): PendingIntent {
        val intent = Intent(context, RotationControlService::class.java).apply {
            action = RotationControlService.ACTION_CHANGE_MODE
            putExtra(RotationControlService.INTENT_NEW_MODE, mode.name)
        }
        return PendingIntent.getService(
            context,
            RotationControlService.ACTION_CHANGE_MODE_REQUEST_CODE_BASE + mode.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
