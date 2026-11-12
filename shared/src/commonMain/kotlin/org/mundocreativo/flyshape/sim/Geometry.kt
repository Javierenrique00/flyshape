package org.mundocreativo.flyshape.sim

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Minimal immutable 2-D vector shared by the simulation and the renderer. */
data class Vec2(val x: Float, val y: Float) {

    operator fun plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)

    operator fun minus(other: Vec2): Vec2 = Vec2(x - other.x, y - other.y)

    operator fun times(scale: Float): Vec2 = Vec2(x * scale, y * scale)

    val length: Float
        get() = sqrt(x * x + y * y)

    fun normalized(): Vec2 {
        val len = length
        return if (len < 1e-6f) ZERO else Vec2(x / len, y / len)
    }

    /** Rotated 90° counter-clockwise. */
    fun perpendicular(): Vec2 = Vec2(-y, x)

    companion object {
        val ZERO = Vec2(0f, 0f)
    }
}

/** Wing sections available in the tunnel. */
enum class WingShape(val label: String, val description: String) {
    Airfoil("Airfoil", "NACA 2412 · cambered, streamlined"),
    Symmetric("Symmetric", "NACA 0012 · symmetric, streamlined"),
    Boxy("Boxy", "Rounded rectangle · bluff section"),
}

/** The rectangular slice of space the tunnel shows, expressed in chord units. */
object TunnelDomain {

    val center = Vec2(0.4f, 0f)
    const val WIDTH = 6f
    const val HEIGHT = 3.5f

    val minX: Float get() = center.x - WIDTH / 2f
    val maxX: Float get() = center.x + WIDTH / 2f
    val minY: Float get() = center.y - HEIGHT / 2f
    val maxY: Float get() = center.y + HEIGHT / 2f

    fun contains(x: Float, y: Float, margin: Float = 0f): Boolean =
        x >= minX - margin && x <= maxX + margin && y >= minY - margin && y <= maxY + margin
}

/**
 * Generates the closed outline of a wing section.
 *
 * Every contour is counter-clockwise and spans exactly one chord unit
 * (`x` from 0 at the leading edge to 1 at the trailing edge), so shapes can be
 * swapped in the tunnel without rescaling anything.
 */
object WingGeometry {

    const val CHORD = 1f
    const val BOX_THICKNESS = 0.22f
    private const val BOX_CORNER_RADIUS = 0.045f
    private const val MAX_THICKNESS = 0.12f
    private const val CAMBER_POSITION = 0.4f

    fun contour(shape: WingShape, pointsPerSide: Int = 50): List<Vec2> = when (shape) {
        WingShape.Airfoil -> naca(camber = 0.02f, pointsPerSide = pointsPerSide)
        WingShape.Symmetric -> naca(camber = 0f, pointsPerSide = pointsPerSide)
        WingShape.Boxy -> roundedBox(points = pointsPerSide * 2)
    }

    /**
     * NACA 4-digit section. The outline starts at the trailing edge, runs along
     * the upper surface to the leading edge and returns along the lower surface.
     */
    private fun naca(camber: Float, pointsPerSide: Int): List<Vec2> {
        val upper = ArrayList<Vec2>(pointsPerSide + 1)
        val lower = ArrayList<Vec2>(pointsPerSide + 1)

        for (index in 0..pointsPerSide) {
            // Cosine spacing: dense near the leading edge where curvature is highest.
            val beta = PI * index / pointsPerSide
            val x = (0.5 * (1.0 + cos(beta))).toFloat()
            val yt = thicknessDistribution(x)

            var yc = 0f
            var slope = 0f
            if (camber > 0f) {
                val p = CAMBER_POSITION
                if (x < p) {
                    yc = camber / (p * p) * (2f * p * x - x * x)
                    slope = 2f * camber / (p * p) * (p - x)
                } else {
                    val q = 1f - p
                    yc = camber / (q * q) * ((1f - 2f * p) + 2f * p * x - x * x)
                    slope = 2f * camber / (q * q) * (p - x)
                }
            }

            val theta = atan(slope.toDouble()).toFloat()
            val sinTheta = sin(theta)
            val cosTheta = cos(theta)

            upper += Vec2(x - yt * sinTheta, yc + yt * cosTheta)
            lower += Vec2(x + yt * sinTheta, yc - yt * cosTheta)
        }

        val contour = ArrayList<Vec2>(2 * pointsPerSide)
        contour.addAll(upper)
        // The leading-edge point is already in `upper`; the trailing edge wraps around.
        for (index in pointsPerSide - 1 downTo 1) {
            contour.add(lower[index])
        }
        return contour
    }

    private fun thicknessDistribution(x: Float): Float {
        if (x <= 0f) return 0f
        val root = sqrt(x)
        return 5f * MAX_THICKNESS * (
            0.2969f * root -
                0.1260f * x -
                0.3516f * x * x +
                0.2843f * x * x * x -
                0.1036f * x * x * x * x
            )
    }

    /**
     * Bluff "boxy" section: a rectangle with rounded corners, closed at the
     * trailing edge mid-height so the Kutta condition has a well defined point.
     */
    private fun roundedBox(points: Int): List<Vec2> {
        val half = BOX_THICKNESS / 2f
        val radius = BOX_CORNER_RADIUS
        val innerX = CHORD - radius
        val innerY = half - radius

        val segments = listOf(
            LineSegment(Vec2(CHORD, 0f), Vec2(CHORD, innerY)),
            ArcSegment(Vec2(innerX, innerY), radius, 0f, 90f),
            LineSegment(Vec2(innerX, half), Vec2(radius, half)),
            ArcSegment(Vec2(radius, innerY), radius, 90f, 180f),
            LineSegment(Vec2(0f, innerY), Vec2(0f, -innerY)),
            ArcSegment(Vec2(radius, -innerY), radius, 180f, 270f),
            LineSegment(Vec2(radius, -half), Vec2(innerX, -half)),
            ArcSegment(Vec2(innerX, -innerY), radius, 270f, 360f),
            LineSegment(Vec2(CHORD, -innerY), Vec2(CHORD, 0f)),
        )

        val totalLength = segments.sumOf { it.length.toDouble() }.toFloat()
        val contour = ArrayList<Vec2>(points)
        var segmentIndex = 0
        var segmentStart = 0f

        for (index in 0 until points) {
            val target = totalLength * index / points
            while (
                segmentIndex < segments.size - 1 &&
                target > segmentStart + segments[segmentIndex].length
            ) {
                segmentStart += segments[segmentIndex].length
                segmentIndex++
            }
            val segment = segments[segmentIndex]
            val t = if (segment.length <= 0f) 0f else (target - segmentStart) / segment.length
            contour += segment.pointAt(t.coerceIn(0f, 1f))
        }
        return contour
    }

    private interface OutlineSegment {
        val length: Float
        fun pointAt(t: Float): Vec2
    }

    private class LineSegment(private val start: Vec2, private val end: Vec2) : OutlineSegment {
        override val length: Float = (end - start).length
        override fun pointAt(t: Float): Vec2 = start + (end - start) * t
    }

    private class ArcSegment(
        private val center: Vec2,
        private val radius: Float,
        private val startDegrees: Float,
        private val endDegrees: Float,
    ) : OutlineSegment {
        override val length: Float = radius * (endDegrees - startDegrees) * (PI.toFloat() / 180f)

        override fun pointAt(t: Float): Vec2 {
            val angle = (startDegrees + (endDegrees - startDegrees) * t) * (PI.toFloat() / 180f)
            return Vec2(center.x + radius * cos(angle), center.y + radius * sin(angle))
        }
    }
}


