package org.mundocreativo.flyshape.sim

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PanelMethodTest {

    /** Textbook check: potential flow doubles in speed over the top of a cylinder. */
    @Test
    fun circularCylinderDoublesTheFlowSpeedOverItsTop() {
        val radius = 0.5f
        val panelCount = 120
        val contour = (0 until panelCount).map { index ->
            val angle = 2.0 * PI * index / panelCount
            Vec2((radius * cos(angle)).toFloat(), (radius * sin(angle)).toFloat())
        }

        val solution = FlowSolution.solveForContour(contour, angleOfAttackDeg = 0f)

        val aboveTop = solution.velocityAt(0f, radius + 0.002f)
        assertEquals(2f, aboveTop.length, 0.08f)

        // Cp = 1 - 4 = -3 at the crown of the cylinder.
        val crownCp = solution.panelCp.min()
        assertTrue(crownCp in -3.3f..-2.6f, "crown Cp was $crownCp")
    }

    @Test
    fun symmetricSectionHasNoLiftAtZeroAngle() {
        val solution = FlowSolution.solve(WingShape.Symmetric, 0f)
        assertTrue(
            abs(solution.liftCoefficient) < 0.01f,
            "Cl should be zero, was ${solution.liftCoefficient}",
        )
    }

    /**
     * Thin-airfoil theory gives Cl = 2π·sin(4°) = 0.438 for a flat plate, and a
     * NACA 0012 is close to that at this angle.
     */
    @Test
    fun symmetricSectionMatchesThinAirfoilTheoryAtFourDegrees() {
        val solution = FlowSolution.solve(WingShape.Symmetric, 4f)
        assertEquals(0.438f, solution.liftCoefficient, 0.09f)
    }

    @Test
    fun camberedSectionLiftsAtZeroAngle() {
        val solution = FlowSolution.solve(WingShape.Airfoil, 0f)
        assertTrue(
            solution.liftCoefficient in 0.08f..0.5f,
            "cambered Cl was ${solution.liftCoefficient}",
        )
    }

    @Test
    fun liftGrowsWithTheAngleOfAttack() {
        val angles = listOf(-6f, -2f, 0f, 2f, 6f, 10f)
        val lifts = angles.map { FlowSolution.solve(WingShape.Symmetric, it).liftCoefficient }
        for (index in 1 until lifts.size) {
            assertTrue(
                lifts[index] > lifts[index - 1],
                "lift should grow at ${angles[index]}°, got $lifts",
            )
        }
    }

    @Test
    fun farFromTheSectionTheFlowIsUndisturbed() {
        val solution = FlowSolution.solve(WingShape.Airfoil, 6f)
        val wind = solution.windDirection
        val far = solution.velocityAt(-40f, 12f)

        assertEquals(wind.x, far.x, 0.01f)
        assertEquals(wind.y, far.y, 0.01f)
    }

    /** The air must run along the skin: almost no velocity through the surface. */
    @Test
    fun flowRunsTangentToTheSkin() {
        val solution = FlowSolution.solve(WingShape.Symmetric, 5f)
        val contour = solution.contour
        val n = contour.size
        val leaks = FloatArray(n)

        for (index in 0 until n) {
            val start = contour[index]
            val end = contour[(index + 1) % n]
            val panelLength = (end - start).length
            val tangent = (end - start).normalized()
            val outward = Vec2(tangent.y, -tangent.x)
            val midX = (start.x + end.x) * 0.5f
            val midY = (start.y + end.y) * 0.5f

            // Probe a fraction of the panel length off the skin, so the sample
            // follows the local scale instead of the very tight leading edge.
            val offset = (0.05f * panelLength).coerceIn(1e-4f, 5e-3f)
            val probe = Vec2(midX + outward.x * offset, midY + outward.y * offset)
            val velocity = solution.velocityAt(probe.x, probe.y)
            leaks[index] = abs(velocity.x * outward.x + velocity.y * outward.y)
        }

        val worstIndex = leaks.indices.maxBy { leaks[it] }
        val worst = leaks[worstIndex]
        val average = leaks.average().toFloat()

        assertTrue(
            worst < 0.02f,
            "panel $worstIndex leaks $worst (average $average) through the skin",
        )
        assertTrue(average < 0.004f, "average leakage $average is too high")
    }

    @Test
    fun boxySectionStaysWellBehaved() {
        val solution = FlowSolution.solve(WingShape.Boxy, 5f)

        assertTrue(solution.liftCoefficient.isFinite(), "Cl was not finite")
        assertTrue(
            abs(solution.liftCoefficient) < 3.5f,
            "boxy Cl was ${solution.liftCoefficient}",
        )
        assertTrue(
            solution.panelCp.all { it.isFinite() && it > -25f },
            "boxy Cp distribution blew up",
        )
        assertTrue(solution.isInsideBody(0.5f, 0f), "chord line should be inside the box")
        assertTrue(!solution.isInsideBody(0.5f, 0.4f), "far above the box is outside")
        assertTrue(!solution.isInsideBody(-0.5f, 0f), "upstream of the box is outside")
    }

    @Test
    fun pressureMapAndStreamlinesStayFiniteEverywhere() {
        val solution = FlowSolution.solve(WingShape.Airfoil, 8f)
        val lines = StreamlineTracer.rake(solution, count = 12)

        assertEquals(12, lines.size)
        assertTrue(lines.all { it.points.size > 5 }, "some streamline died immediately")
        assertTrue(
            lines.all { line -> line.points.all { it.x.isFinite() && it.y.isFinite() } },
            "a streamline escaped to infinity",
        )

        for (x in -2..3) {
            for (y in -1..1) {
                val cp = solution.pressureCoefficientAt(x.toFloat(), y.toFloat())
                assertTrue(cp.isFinite(), "Cp at ($x,$y) was $cp")
            }
        }
    }
}
