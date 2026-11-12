package org.mundocreativo.flyshape.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AerodynamicsTest {

    @Test
    fun cruiseNumbersArePlausible() {
        val metrics = AeroMetrics.of(FlowSolution.solve(WingShape.Airfoil, 6f))

        assertTrue(!metrics.stalled, "6° should not be stalled")
        assertTrue(metrics.liftCoefficient in 0.3f..1.1f, "Cl was ${metrics.liftCoefficient}")
        assertTrue(metrics.dragCoefficient > 0f, "Cd was ${metrics.dragCoefficient}")
        assertTrue(metrics.liftToDrag > 5f, "L/D was ${metrics.liftToDrag}")
    }

    @Test
    fun stallFlagAndLiftSagPastTheStallAngle() {
        val cruise = AeroMetrics.of(FlowSolution.solve(WingShape.Airfoil, 13f))
        val stalled = AeroMetrics.of(FlowSolution.solve(WingShape.Airfoil, 18f))

        assertTrue(!cruise.stalled, "13° should still be attached")
        assertTrue(stalled.stalled, "18° should be flagged as stalled")
        assertTrue(
            stalled.liftCoefficient < cruise.liftCoefficient,
            "lift should sag: ${stalled.liftCoefficient} vs ${cruise.liftCoefficient}",
        )
        assertTrue(
            stalled.dragCoefficient > cruise.dragCoefficient,
            "drag should rise: ${stalled.dragCoefficient} vs ${cruise.dragCoefficient}",
        )
    }

    @Test
    fun dynamicPressureMatchesTheStandardFormula() {
        // q = ½·ρ·V² with ρ = 1.225 kg/m³, at 20 m/s → 245 Pa.
        assertEquals(245f, AeroMetrics.dynamicPressure(20f), 0.5f)
    }
}
