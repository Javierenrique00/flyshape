package org.mundocreativo.flyshape.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mundocreativo.flyshape.sim.WingShape

/** Everything the operator can change about the tunnel. */
@Stable
class WindTunnelState {

    var windSpeed by mutableFloatStateOf(DEFAULT_SPEED)
    var windAngleDeg by mutableFloatStateOf(DEFAULT_ANGLE)
    var shape by mutableStateOf(WingShape.Airfoil)

    var showPressure by mutableStateOf(true)
    var showStreamlines by mutableStateOf(true)
    var showParticles by mutableStateOf(true)
    var showGrid by mutableStateOf(true)

    /** False freezes the smoke so a snapshot can be studied. */
    var running by mutableStateOf(true)

    fun reset() {
        windSpeed = DEFAULT_SPEED
        windAngleDeg = DEFAULT_ANGLE
        shape = WingShape.Airfoil
        showPressure = true
        showStreamlines = true
        showParticles = true
        showGrid = true
        running = true
    }

    companion object {
        const val MIN_SPEED = 0f
        const val MAX_SPEED = 60f
        const val MIN_ANGLE = -25f
        const val MAX_ANGLE = 25f
        private const val DEFAULT_SPEED = 26f
        private const val DEFAULT_ANGLE = 6f
    }
}
