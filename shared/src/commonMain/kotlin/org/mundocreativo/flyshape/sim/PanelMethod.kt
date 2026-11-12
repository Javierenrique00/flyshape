package org.mundocreativo.flyshape.sim

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin

/**
 * Steady, inviscid, incompressible flow around a 2-D wing section.
 *
 * The flow is solved with a Hess–Smith panel method: every panel carries a
 * constant-strength source distribution and the whole contour carries one
 * constant-strength vortex distribution, closed by a Kutta condition at the
 * trailing edge. That makes the model valid for any closed contour, which is
 * what lets the tunnel show a streamlined airfoil and a boxy section with the
 * same solver.
 *
 * The solution is computed for a freestream of unit speed. Because the problem
 * is linear, a change of wind speed is a pure scaling — so the solver only has
 * to run again when the angle or the shape changes.
 */
class FlowSolution private constructor(
    /** Closed contour of the section, counter-clockwise, in chord units. */
    val contour: List<Vec2>,
    val angleOfAttackDeg: Float,
    private val panelLength: DoubleArray,
    private val tangentX: DoubleArray,
    private val tangentY: DoubleArray,
    private val sourceStrength: DoubleArray,
    private val vortexStrength: Double,
    /** Pressure coefficient sampled at each panel midpoint. */
    val panelCp: FloatArray,
    /** Inviscid lift coefficient per unit span (chord normalised). */
    val liftCoefficient: Float,
    /** Inviscid drag coefficient; only the pressure part, skin friction is absent. */
    val dragCoefficient: Float,
    /** Pitching moment about the quarter chord, positive nose-up. */
    val momentCoefficient: Float,
    /** Unit vector the airstream is travelling along. */
    val windDirection: Vec2,
) {

    val panelCount: Int get() = panelLength.size

    /** True when the point lies inside the solid section. */
    fun isInsideBody(x: Float, y: Float): Boolean {
        var inside = false
        val n = contour.size
        var j = n - 1
        for (i in 0 until n) {
            val xi = contour[i].x
            val yi = contour[i].y
            val xj = contour[j].x
            val yj = contour[j].y
            if ((yi > y) != (yj > y)) {
                val crossX = xi + (y - yi) / (yj - yi) * (xj - xi)
                if (x < crossX) inside = !inside
            }
            j = i
        }
        return inside
    }

    /**
     * Velocity at an arbitrary point, in units of the freestream speed, written
     * into [out] as `[u, v]`. Allocation-free: this runs for every particle on
     * every frame.
     */
    fun velocityInto(x: Float, y: Float, out: FloatArray) {
        val n = panelCount
        val px = x.toDouble()
        val py = y.toDouble()
        var u = windDirection.x.toDouble()
        var v = windDirection.y.toDouble()

        for (j in 0 until n) {
            val startX = contour[j].x.toDouble()
            val startY = contour[j].y.toDouble()
            val cs = tangentX[j]
            val sn = tangentY[j]

            val rx = px - startX
            val ry = py - startY
            val xi = rx * cs + ry * sn
            val eta = -rx * sn + ry * cs

            val r1 = hypot(xi, eta).coerceAtLeast(MIN_RADIUS)
            val r2 = hypot(xi - panelLength[j], eta).coerceAtLeast(MIN_RADIUS)

            var theta = atan2(eta, xi - panelLength[j]) - atan2(eta, xi)
            if (theta > PI) theta -= TWO_PI else if (theta < -PI) theta += TWO_PI

            val logTerm = ln(r1 / r2) / TWO_PI
            val angleTerm = theta / TWO_PI

            val su = logTerm * cs - angleTerm * sn
            val sv = logTerm * sn + angleTerm * cs
            val vu = angleTerm * cs + logTerm * sn
            val vv = angleTerm * sn - logTerm * cs

            val q = sourceStrength[j]
            u += q * su + vortexStrength * vu
            v += q * sv + vortexStrength * vv
        }

        out[0] = u.toFloat()
        out[1] = v.toFloat()
    }

    fun velocityAt(x: Float, y: Float): Vec2 {
        val out = FloatArray(2)
        velocityInto(x, y, out)
        return Vec2(out[0], out[1])
    }

    /** Pressure coefficient `Cp = 1 - (V/V∞)²`, valid anywhere in the field. */
    fun pressureCoefficientAt(x: Float, y: Float): Float {
        val out = FloatArray(2)
        velocityInto(x, y, out)
        val speedSquared = out[0] * out[0] + out[1] * out[1]
        return 1f - speedSquared
    }

    companion object {
        private const val TWO_PI = 2.0 * PI
        private const val MIN_RADIUS = 1e-5
        private const val QUARTER_CHORD = 0.25

        /** Solves the flow around one of the tunnel's wing sections. */
        fun solve(shape: WingShape, angleOfAttackDeg: Float): FlowSolution =
            solveForContour(WingGeometry.contour(shape), angleOfAttackDeg)

        /**
         * Solves the flow around an arbitrary counter-clockwise contour.
         * Exposed so the solver can be validated against known cases
         * (a circular cylinder, a flat plate) in unit tests.
         */
        fun solveForContour(
            contour: List<Vec2>,
            angleOfAttackDeg: Float,
        ): FlowSolution {
            val n = contour.size
            require(n >= 8) { "A closed contour needs at least 8 points, got $n" }

            val panelLength = DoubleArray(n)
            val tangentX = DoubleArray(n)
            val tangentY = DoubleArray(n)
            val normalX = DoubleArray(n)
            val normalY = DoubleArray(n)
            val midX = DoubleArray(n)
            val midY = DoubleArray(n)

            for (i in 0 until n) {
                val start = contour[i]
                val end = contour[(i + 1) % n]
                val dx = (end.x - start.x).toDouble()
                val dy = (end.y - start.y).toDouble()
                val len = hypot(dx, dy)
                require(len > 1e-7) { "Degenerate panel $i" }
                panelLength[i] = len
                tangentX[i] = dx / len
                tangentY[i] = dy / len
                // Contours run counter-clockwise, so the outward normal is the
                // tangent rotated clockwise.
                normalX[i] = tangentY[i]
                normalY[i] = -tangentX[i]
                midX[i] = (start.x.toDouble() + end.x) * 0.5
                midY[i] = (start.y.toDouble() + end.y) * 0.5
            }

            val windAngle = angleOfAttackDeg * PI / 180.0
            val windX = cos(windAngle)
            val windY = sin(windAngle)

            val sourceU = DoubleArray(n * n)
            val sourceV = DoubleArray(n * n)
            val vortexU = DoubleArray(n * n)
            val vortexV = DoubleArray(n * n)

            val systemSize = n + 1
            val matrix = Array(systemSize) { DoubleArray(systemSize) }
            val rhs = DoubleArray(systemSize)
            val influence = DoubleArray(4)

            // Flow tangency at every control point.
            for (i in 0 until n) {
                rhs[i] = -(windX * normalX[i] + windY * normalY[i])
                for (j in 0 until n) {
                    val index = i * n + j
                    if (i == j) {
                        // The panel's own source sheet pushes the flow outward along
                        // its normal; its own vortex sheet drives it along the tangent.
                        matrix[i][j] = 0.5
                        sourceU[index] = 0.0
                        sourceV[index] = 0.0
                        vortexU[index] = -0.5 * tangentX[i]
                        vortexV[index] = -0.5 * tangentY[i]
                    } else {
                        panelInfluence(
                            ax = contour[j].x.toDouble(),
                            ay = contour[j].y.toDouble(),
                            bx = contour[(j + 1) % n].x.toDouble(),
                            by = contour[(j + 1) % n].y.toDouble(),
                            px = midX[i],
                            py = midY[i],
                            out = influence,
                        )
                        sourceU[index] = influence[0]
                        sourceV[index] = influence[1]
                        vortexU[index] = influence[2]
                        vortexV[index] = influence[3]
                        matrix[i][j] = influence[0] * normalX[i] + influence[1] * normalY[i]
                        matrix[i][n] += influence[2] * normalX[i] + influence[3] * normalY[i]
                    }
                }
            }

            // Kutta condition: the flow leaves the trailing edge smoothly, which on
            // this contour ordering means equal and opposite tangential velocities
            // on the first and the last panel.
            val firstPanel = 0
            val lastPanel = n - 1
            for (j in 0 until n) {
                // The first panel is index 0, so its influence row starts at j.
                val firstIndex = j
                val lastIndex = lastPanel * n + j
                matrix[n][j] =
                    sourceU[firstIndex] * tangentX[firstPanel] +
                        sourceV[firstIndex] * tangentY[firstPanel] +
                        sourceU[lastIndex] * tangentX[lastPanel] +
                        sourceV[lastIndex] * tangentY[lastPanel]
                matrix[n][n] +=
                    vortexU[firstIndex] * tangentX[firstPanel] +
                        vortexV[firstIndex] * tangentY[firstPanel] +
                        vortexU[lastIndex] * tangentX[lastPanel] +
                        vortexV[lastIndex] * tangentY[lastPanel]
            }
            rhs[n] =
                -(windX * tangentX[firstPanel] + windY * tangentY[firstPanel]) -
                    (windX * tangentX[lastPanel] + windY * tangentY[lastPanel])

            val unknowns = solveLinearSystem(matrix, rhs)
            val sourceStrength = unknowns.copyOfRange(0, n)
            val vortexStrength = unknowns[n]

            return buildSolution(
                contour = contour,
                angleOfAttackDeg = angleOfAttackDeg,
                windX = windX,
                windY = windY,
                panelLength = panelLength,
                tangentX = tangentX,
                tangentY = tangentY,
                normalX = normalX,
                normalY = normalY,
                midX = midX,
                midY = midY,
                sourceU = sourceU,
                sourceV = sourceV,
                vortexU = vortexU,
                vortexV = vortexV,
                sourceStrength = sourceStrength,
                vortexStrength = vortexStrength,
            )
        }

        /**
         * Velocity induced at `(px, py)` by a panel carrying unit source strength
         * and unit vortex strength, in the world frame as
         * `[u_source, v_source, u_vortex, v_vortex]`.
         */
        private fun panelInfluence(
            ax: Double,
            ay: Double,
            bx: Double,
            by: Double,
            px: Double,
            py: Double,
            out: DoubleArray,
        ) {
            val dx = bx - ax
            val dy = by - ay
            val len = hypot(dx, dy)
            val cs = dx / len
            val sn = dy / len

            // Panel frame: xi runs along the panel, eta along its left normal.
            val rx = px - ax
            val ry = py - ay
            val xi = rx * cs + ry * sn
            val eta = -rx * sn + ry * cs

            val r1 = hypot(xi, eta).coerceAtLeast(MIN_RADIUS)
            val r2 = hypot(xi - len, eta).coerceAtLeast(MIN_RADIUS)

            var theta = atan2(eta, xi - len) - atan2(eta, xi)
            if (theta > PI) theta -= TWO_PI else if (theta < -PI) theta += TWO_PI

            val logTerm = ln(r1 / r2) / TWO_PI
            val angleTerm = theta / TWO_PI

            out[0] = logTerm * cs - angleTerm * sn
            out[1] = logTerm * sn + angleTerm * cs
            out[2] = angleTerm * cs + logTerm * sn
            out[3] = angleTerm * sn - logTerm * cs
        }

        /** Gauss elimination with partial pivoting; the system is dense and small. */
        private fun solveLinearSystem(matrix: Array<DoubleArray>, rhs: DoubleArray): DoubleArray {
            val n = rhs.size
            val solution = rhs.copyOf()

            for (column in 0 until n) {
                var pivot = column
                var best = abs(matrix[column][column])
                for (row in column + 1 until n) {
                    val candidate = abs(matrix[row][column])
                    if (candidate > best) {
                        best = candidate
                        pivot = row
                    }
                }
                require(best > 1e-12) { "Singular panel system at column $column" }
                if (pivot != column) {
                    val swapRow = matrix[pivot]
                    matrix[pivot] = matrix[column]
                    matrix[column] = swapRow
                    val swapValue = solution[pivot]
                    solution[pivot] = solution[column]
                    solution[column] = swapValue
                }
                val diagonal = matrix[column][column]
                for (row in column + 1 until n) {
                    val factor = matrix[row][column] / diagonal
                    if (factor == 0.0) continue
                    for (k in column until n) {
                        matrix[row][k] -= factor * matrix[column][k]
                    }
                    solution[row] -= factor * solution[column]
                }
            }

            for (row in n - 1 downTo 0) {
                var sum = solution[row]
                for (k in row + 1 until n) {
                    sum -= matrix[row][k] * solution[k]
                }
                solution[row] = sum / matrix[row][row]
            }
            return solution
        }

        /** Post-processes the panel strengths into surface pressures and forces. */
        private fun buildSolution(
            contour: List<Vec2>,
            angleOfAttackDeg: Float,
            windX: Double,
            windY: Double,
            panelLength: DoubleArray,
            tangentX: DoubleArray,
            tangentY: DoubleArray,
            normalX: DoubleArray,
            normalY: DoubleArray,
            midX: DoubleArray,
            midY: DoubleArray,
            sourceU: DoubleArray,
            sourceV: DoubleArray,
            vortexU: DoubleArray,
            vortexV: DoubleArray,
            sourceStrength: DoubleArray,
            vortexStrength: Double,
        ): FlowSolution {
            val n = contour.size
            val panelCp = FloatArray(n)
            var forceX = 0.0
            var forceY = 0.0
            var moment = 0.0

            for (i in 0 until n) {
                var tangential = windX * tangentX[i] + windY * tangentY[i]
                for (j in 0 until n) {
                    val index = i * n + j
                    tangential += sourceStrength[j] *
                        (sourceU[index] * tangentX[i] + sourceV[index] * tangentY[i])
                    tangential += vortexStrength *
                        (vortexU[index] * tangentX[i] + vortexV[index] * tangentY[i])
                }

                val cp = 1.0 - tangential * tangential
                panelCp[i] = cp.toFloat()

                // Pressure acts along the inward normal: dF = -Cp * n_out * ds.
                val ds = panelLength[i]
                val fx = -cp * normalX[i] * ds
                val fy = -cp * normalY[i] * ds
                forceX += fx
                forceY += fy
                moment += (midX[i] - QUARTER_CHORD) * fy - midY[i] * fx
            }

            // Resolve the force onto the wind axes.
            val lift = forceX * -windY + forceY * windX
            val drag = forceX * windX + forceY * windY

            return FlowSolution(
                contour = contour,
                angleOfAttackDeg = angleOfAttackDeg,
                panelLength = panelLength,
                tangentX = tangentX,
                tangentY = tangentY,
                sourceStrength = sourceStrength,
                vortexStrength = vortexStrength,
                panelCp = panelCp,
                liftCoefficient = lift.toFloat(),
                dragCoefficient = drag.toFloat(),
                momentCoefficient = moment.toFloat(),
                windDirection = Vec2(windX.toFloat(), windY.toFloat()),
            )
        }
    }
}
