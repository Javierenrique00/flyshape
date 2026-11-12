package org.mundocreativo.flyshape.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import org.mundocreativo.flyshape.sim.FlowSolution
import org.mundocreativo.flyshape.sim.ParticleField
import org.mundocreativo.flyshape.sim.Streamline
import org.mundocreativo.flyshape.sim.TunnelDomain
import org.mundocreativo.flyshape.sim.Vec2
import org.mundocreativo.flyshape.sim.AeroMetrics
import kotlin.math.abs
import kotlin.math.sqrt

internal fun DrawScope.drawTunnelBackground() {
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFF0A1119), Color(0xFF12202C), Color(0xFF090F16)),
        ),
    )
}

/** Reference grid plus the frame that marks the working section. */
internal fun DrawScope.drawTunnelGrid(camera: TunnelCamera) {
    val left = camera.x(TunnelDomain.minX)
    val right = camera.x(TunnelDomain.maxX)
    val top = camera.y(TunnelDomain.maxY)
    val bottom = camera.y(TunnelDomain.minY)
    val gridColor = TunnelPalette.GridLine.copy(alpha = 0.06f)

    val step = 0.25f
    var worldX = TunnelDomain.minX
    while (worldX <= TunnelDomain.maxX + 1e-4f) {
        val screenX = camera.x(worldX)
        drawLine(gridColor, Offset(screenX, top), Offset(screenX, bottom), strokeWidth = 1f)
        worldX += step
    }

    var worldY = TunnelDomain.minY
    while (worldY <= TunnelDomain.maxY + 1e-4f) {
        val screenY = camera.y(worldY)
        drawLine(gridColor, Offset(left, screenY), Offset(right, screenY), strokeWidth = 1f)
        worldY += step
    }

    drawRect(
        color = TunnelPalette.Accent.copy(alpha = 0.18f),
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
        style = Stroke(width = 1.5f),
    )
}

/** Coarse pressure map: warm where the air is compressed, cold where it is sucked. */
internal fun DrawScope.drawPressureField(camera: TunnelCamera, solution: FlowSolution) {
    val cell = 0.25f
    var worldX = TunnelDomain.minX
    while (worldX < TunnelDomain.maxX) {
        var worldY = TunnelDomain.minY
        while (worldY < TunnelDomain.maxY) {
            val centreX = worldX + cell * 0.5f
            val centreY = worldY + cell * 0.5f
            if (!solution.isInsideBody(centreX, centreY)) {
                val color = pressureColor(solution.pressureCoefficientAt(centreX, centreY))
                if (color.alpha > 0.02f) {
                    drawRect(
                        color = color,
                        topLeft = Offset(camera.x(worldX), camera.y(worldY + cell)),
                        size = Size(camera.length(cell) + 1f, camera.length(cell) + 1f),
                    )
                }
            }
            worldY += cell
        }
        worldX += cell
    }
}

/** Path lines, drawn in short chunks so the colour can follow the local speed. */
internal fun DrawScope.drawStreamlines(camera: TunnelCamera, streamlines: List<Streamline>) {
    for (streamline in streamlines) {
        val points = streamline.points
        if (points.size < 2) continue

        var index = 0
        while (index < points.size - 1) {
            val end = minOf(index + 6, points.size - 1)
            val path = Path()
            path.moveTo(camera.x(points[index].x), camera.y(points[index].y))
            var speedSum = 0f
            for (point in index + 1..end) {
                path.lineTo(camera.x(points[point].x), camera.y(points[point].y))
                speedSum += streamline.speeds[point]
            }
            drawPath(
                path = path,
                color = speedColor(speedSum / (end - index)),
                style = Stroke(width = 1.5f, cap = StrokeCap.Round),
            )
            index = end
        }
    }
}

/** The smoke: every tracer is a streak pointing back along its own velocity. */
internal fun DrawScope.drawParticles(
    camera: TunnelCamera,
    particles: ParticleField,
    trailSeconds: Float = 0.30f,
) {
    for (index in 0 until particles.count) {
        val worldX = particles.x(index)
        val worldY = particles.y(index)
        val head = Offset(camera.x(worldX), camera.y(worldY))
        val tail = Offset(
            camera.x(worldX - particles.vx(index) * trailSeconds),
            camera.y(worldY - particles.vy(index) * trailSeconds),
        )
        drawLine(
            color = TunnelPalette.Particle.copy(alpha = 0.40f),
            start = tail,
            end = head,
            strokeWidth = 1.5f,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = TunnelPalette.Particle.copy(alpha = 0.85f),
            radius = 1.4f,
            center = head,
        )
    }
}


/** The wing section itself, with its chord line. */
internal fun DrawScope.drawWing(camera: TunnelCamera, contour: List<Vec2>) {
    if (contour.size < 3) return

    val path = Path()
    path.moveTo(camera.x(contour[0].x), camera.y(contour[0].y))
    for (index in 1 until contour.size) {
        path.lineTo(camera.x(contour[index].x), camera.y(contour[index].y))
    }
    path.close()

    val bodyTop = camera.y(contour.maxOf { it.y })
    val bodyBottom = camera.y(contour.minOf { it.y })
    if (bodyBottom - bodyTop > 2f) {
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(TunnelPalette.BodyTop, TunnelPalette.BodyBottom),
                startY = bodyTop,
                endY = bodyBottom,
            ),
        )
    } else {
        drawPath(path = path, color = TunnelPalette.BodyTop)
    }

    // Soft halo, so the section still reads clearly over the pressure map.
    drawPath(
        path = path,
        color = TunnelPalette.BodyEdge.copy(alpha = 0.16f),
        style = Stroke(width = 8f),
    )

    drawPath(
        path = path,
        color = TunnelPalette.BodyEdge.copy(alpha = 0.95f),
        style = Stroke(width = 2f),
    )

    // Chord line, for reference.
    drawLine(
        color = TunnelPalette.TextMuted.copy(alpha = 0.55f),
        start = Offset(camera.x(0f), camera.y(0f)),
        end = Offset(camera.x(1f), camera.y(0f)),
        strokeWidth = 1f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 7f)),
    )
}

/** A small rake of arrows showing which way the wind is blowing. */
internal fun DrawScope.drawWindArrows(camera: TunnelCamera, wind: Vec2, windSpeed: Float) {
    if (windSpeed <= 0.05f) return

    val arrowLength = 0.42f
    repeat(3) { row ->
        val worldY = TunnelDomain.maxY - 0.30f - row * 0.26f
        val worldX = TunnelDomain.minX + 0.18f
        drawArrow(
            from = Offset(camera.x(worldX), camera.y(worldY)),
            to = Offset(
                camera.x(worldX + wind.x * arrowLength),
                camera.y(worldY + wind.y * arrowLength),
            ),
            color = TunnelPalette.Accent.copy(alpha = 0.70f),
            strokeWidth = 2f,
        )
    }
}

/** Lift and drag resolved at the quarter chord, scaled to stay on screen. */
internal fun DrawScope.drawForces(
    camera: TunnelCamera,
    solution: FlowSolution,
    metrics: AeroMetrics,
) {
    val pivot = Vec2(0.25f, 0f)
    val origin = Offset(camera.x(pivot.x), camera.y(pivot.y))
    val wind = solution.windDirection
    val liftDirection = Vec2(-wind.y, wind.x)

    val liftLength = (metrics.liftCoefficient * 0.55f).coerceIn(-0.9f, 0.9f)
    val dragLength = (metrics.dragCoefficient * 6f).coerceIn(0f, 0.9f)

    if (abs(liftLength) > 0.03f) {
        drawArrow(
            from = origin,
            to = Offset(
                camera.x(pivot.x + liftDirection.x * liftLength),
                camera.y(pivot.y + liftDirection.y * liftLength),
            ),
            color = TunnelPalette.Lift,
            strokeWidth = 3f,
        )
    }
    if (dragLength > 0.03f) {
        drawArrow(
            from = origin,
            to = Offset(
                camera.x(pivot.x + wind.x * dragLength),
                camera.y(pivot.y + wind.y * dragLength),
            ),
            color = TunnelPalette.Drag,
            strokeWidth = 3f,
        )
    }

    drawCircle(TunnelPalette.TextMuted, radius = 2.5f, center = origin)
}

private fun DrawScope.drawArrow(from: Offset, to: Offset, color: Color, strokeWidth: Float) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = sqrt(dx * dx + dy * dy)
    if (length < 1f) return

    drawLine(color, from, to, strokeWidth = strokeWidth, cap = StrokeCap.Round)

    val ux = dx / length
    val uy = dy / length
    val head = (strokeWidth * 4.5f).coerceIn(6f, 13f)
    val sideX = -uy * head * 0.55f
    val sideY = ux * head * 0.55f
    val arrowHead = Path().apply {
        moveTo(to.x, to.y)
        lineTo(to.x - ux * head + sideX, to.y - uy * head + sideY)
        lineTo(to.x - ux * head - sideX, to.y - uy * head - sideY)
        close()
    }
    drawPath(arrowHead, color)
}
