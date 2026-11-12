package org.mundocreativo.flyshape.sim

import kotlin.math.sqrt

/**
 * A traced path line through the flow field.
 *
 * [speeds] holds the local speed divided by the freestream speed, sampled at
 * each point, so the renderer can tint the line by how fast the air is moving.
 */
class Streamline(val points: List<Vec2>, val speeds: FloatArray)

/** Tracks path lines through a [FlowSolution] using second-order Runge–Kutta. */
object StreamlineTracer {

    private const val STEP = 0.028f
    private const val MAX_STEPS = 280
    private const val MIN_SPEED = 1e-4f

    /**
     * A vertical rake of path lines on the upwind edge, like the smoke rake of a
     * real wind tunnel.
     */
    fun rake(solution: FlowSolution, count: Int = 22): List<Streamline> {
        val lines = ArrayList<Streamline>(count)
        val top = TunnelDomain.maxY - 0.12f
        val bottom = TunnelDomain.minY + 0.12f
        for (index in 0 until count) {
            val fraction = if (count == 1) 0.5f else index / (count - 1).toFloat()
            val y = bottom + fraction * (top - bottom)
            lines += trace(solution, Vec2(TunnelDomain.minX + 0.05f, y))
        }
        return lines
    }

    /** Traces one path line downstream from [start]. */
    fun trace(solution: FlowSolution, start: Vec2): Streamline {
        val points = ArrayList<Vec2>(MAX_STEPS)
        val speeds = ArrayList<Float>(MAX_STEPS)
        val here = FloatArray(2)
        val midpoint = FloatArray(2)

        var x = start.x
        var y = start.y

        var traced = 0
        while (traced < MAX_STEPS) {
            solution.velocityInto(x, y, here)
            val speed = sqrt(here[0] * here[0] + here[1] * here[1])
            if (speed < MIN_SPEED) break
            points += Vec2(x, y)
            speeds += speed

            val midX = x + 0.5f * STEP * here[0] / speed
            val midY = y + 0.5f * STEP * here[1] / speed
            if (solution.isInsideBody(midX, midY)) break

            solution.velocityInto(midX, midY, midpoint)
            val midSpeed = sqrt(midpoint[0] * midpoint[0] + midpoint[1] * midpoint[1])
            if (midSpeed < MIN_SPEED) break

            x += STEP * midpoint[0] / midSpeed
            y += STEP * midpoint[1] / midSpeed

            if (!TunnelDomain.contains(x, y, margin = 0.45f)) break
            if (solution.isInsideBody(x, y)) break
            traced++
        }

        return Streamline(points, speeds.toFloatArray())
    }
}
