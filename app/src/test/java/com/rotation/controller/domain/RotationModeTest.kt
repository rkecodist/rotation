package com.rotation.controller.domain

import android.view.Surface
import org.junit.Assert.*
import org.junit.Test

class RotationModeTest {

    @Test
    fun `test fromRotationValue returns correct mode`() {
        assertEquals(RotationMode.PORTRAIT, RotationMode.fromRotationValue(Surface.ROTATION_0))
        assertEquals(RotationMode.LANDSCAPE, RotationMode.fromRotationValue(Surface.ROTATION_90))
        assertEquals(RotationMode.PORTRAIT_REVERSE, RotationMode.fromRotationValue(Surface.ROTATION_180))
        assertEquals(RotationMode.LANDSCAPE_REVERSE, RotationMode.fromRotationValue(Surface.ROTATION_270))
    }

    @Test
    fun `test fromRotationValue returns PORTRAIT for unknown`() {
        assertEquals(RotationMode.PORTRAIT, RotationMode.fromRotationValue(999))
    }

    @Test
    fun `test shouldUseAccelerometerRotation`() {
        assertTrue(RotationMode.AUTO.shouldUseAccelerometerRotation)
        assertFalse(RotationMode.PORTRAIT.shouldUseAccelerometerRotation)
    }

    @Test
    fun `test requiresGuard`() {
        assertFalse(RotationMode.AUTO.requiresGuard)
        assertTrue(RotationMode.PORTRAIT_SENSOR.requiresGuard)
        assertFalse(RotationMode.PORTRAIT.requiresGuard)
    }
}
