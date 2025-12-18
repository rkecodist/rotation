package com.rotation.controller.core

import android.content.Context
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.rotation.controller.domain.RotationMode

class RotationControllerImpl(private val context: Context) : RotationController {

    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null

    override fun applyMode(mode: RotationMode, useGuard: Boolean) {
        if (useGuard || mode.requiresGuard) {
            applyOverlayStrategy(mode)
        } else {
            applySystemSettingsStrategy(mode)
        }
    }

    override fun reset() {
        removeOverlay()
    }

    private fun applyOverlayStrategy(mode: RotationMode) {
        val layoutParams = WindowManager.LayoutParams(
            0,
            0,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            Gravity.TOP
        ).apply {
            screenOrientation = mode.orientationValue
        }

        if (overlayView == null) {
            overlayView = View(context)
            try {
                windowManager.addView(overlayView, layoutParams)
            } catch (e: Exception) {
                // Handle permission denial or other issues
                e.printStackTrace()
            }
        } else {
            try {
                windowManager.updateViewLayout(overlayView, layoutParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Also ensure accelerometer is on, as some modes might depend on it underneath or to keep screen alive logic happy?
        // Original code sets ACCELEROMETER_ROTATION to 1 when guard is on.
        Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1)
    }

    private fun applySystemSettingsStrategy(mode: RotationMode) {
        removeOverlay()

        if (mode.shouldUseAccelerometerRotation) {
            Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1)
        } else {
            Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
            Settings.System.putInt(context.contentResolver, Settings.System.USER_ROTATION, mode.rotationValue)
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
        }
    }
}
