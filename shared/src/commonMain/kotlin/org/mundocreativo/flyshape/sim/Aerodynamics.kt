package org.mundocreativo.flyshape.sim

import kotlin.math.PI
import kotlin.math.abs

/**
 * The numbers a wind tunnel operator would read off the instrumentation.
 *
 * The panel solution is inviscid and two-dimensional, so these values are
 * corrected for a finite wing and for stall with simple empirical factors. The
 * streamlines stay those of the potential flow; only the readouts are corrected.
 */
data class AeroMetrics(
    val liftCoefficient: Float,
    val dragCoefficient: Float,
    val momentCoefficient: Float,
    val liftToDrag: Float,
    val stalled: Boolean,
) {

    companion object {

        const val ASPECT_RATIO = 6f
        const val STALL_ANGLE_DEG = 14.5f
        const val AIR_DENSITY = 1.225f

        fun of(solution: FlowSolution): AeroMetrics {
            val alpha = abs(solution.angleOfAttackDeg)
            val stalled = alpha > STALL_ANGLE_DEG

            // Finite-wing lift slope reduction.
            val finiteWing = ASPECT_RATIO / (ASPECT_RATIO + 2f)
            // Beyond the stall angle the lift sags away.
            val liftAvailable =
                if (stalled) (1f - (alpha - STALL_ANGLE_DEG) * 0.10f).coerceIn(0.35f, 1f) else 1f

            val lift = (solution.liftCoefficient * finiteWing * liftAvailable)
                .coerceIn(-2.6f, 2.6f)

            val induced = lift * lift / (PI.toFloat() * ASPECT_RATIO)
            val profile = 0.0075f + 0.022f * (alpha / 18f)
            val pressure = solution.dragCoefficient.coerceIn(0f, 0.25f)
            val stallDrag = if (stalled) 0.10f * (alpha - STALL_ANGLE_DEG) else 0f
            val drag = profile + pressure + induced + stallDrag

            return AeroMetrics(
                liftCoefficient = lift,
                dragCoefficient = drag,
                momentCoefficient = solution.momentCoefficient,
                liftToDrag = if (drag > 1e-4f) lift / drag else 0f,
                stalled = stalled,
            )
        }

        /** Dynamic pressure of the airstream, in pascals. */
        fun dynamicPressure(windSpeed: Float): Float =
            0.5f * AIR_DENSITY * windSpeed * windSpeed
    }
}
