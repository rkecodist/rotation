package com.rotation.controller.core

import com.rotation.controller.domain.RotationMode

interface RotationController {
    fun applyMode(mode: RotationMode, useGuard: Boolean)
    fun reset()
}
