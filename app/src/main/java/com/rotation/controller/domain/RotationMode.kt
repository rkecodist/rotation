package com.rotation.controller.domain

import android.content.pm.ActivityInfo
import android.view.Surface
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import com.rotation.controller.R

enum class RotationMode(
    @IdRes val viewId: Int,
    @StringRes val stringId: Int,
    @DrawableRes val drawableId: Int,
    val rotationValue: Int,
    val orientationValue: Int
) {
    AUTO(
        R.id.mode_auto,
        R.string.mode_auto,
        R.drawable.mode_auto,
        -1,
        ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
    ),

    PORTRAIT(
        R.id.mode_portrait,
        R.string.mode_portrait,
        R.drawable.mode_portrait,
        Surface.ROTATION_0,
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    ),

    PORTRAIT_REVERSE(
        R.id.mode_portrait_reverse,
        R.string.mode_portrait_reverse,
        R.drawable.mode_portrait_reverse,
        Surface.ROTATION_180,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
    ),

    PORTRAIT_SENSOR(
        R.id.mode_portrait_sensor,
        R.string.mode_portrait_sensor,
        R.drawable.mode_portrait_sensor,
        -1,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    ),

    LANDSCAPE(
        R.id.mode_landscape,
        R.string.mode_landscape,
        R.drawable.mode_landscape,
        Surface.ROTATION_90,
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    ),

    LANDSCAPE_REVERSE(
        R.id.mode_landscape_reverse,
        R.string.mode_landscape_reverse,
        R.drawable.mode_landscape_reverse,
        Surface.ROTATION_270,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
    ),

    LANDSCAPE_SENSOR(
        R.id.mode_landscape_sensor,
        R.string.mode_landscape_sensor,
        R.drawable.mode_landscape_sensor,
        -1,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    );

    val shouldUseAccelerometerRotation: Boolean
        get() = this == AUTO

    val requiresGuard: Boolean
        get() = this != AUTO && rotationValue == -1

    companion object {
        fun fromRotationValue(value: Int): RotationMode {
            return entries.find { it.rotationValue == value } ?: PORTRAIT
        }

        fun fromViewId(viewId: Int): RotationMode? {
            return entries.find { it.viewId == viewId }
        }

        fun fromPreferences(context: android.content.Context): RotationMode {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            val name = prefs.getString(context.getString(R.string.mode_key), null)
            return try {
                if (name != null) valueOf(name) else AUTO
            } catch (e: IllegalArgumentException) {
                AUTO
            }
        }
    }
}
