package org.mundocreativo.flyshape.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mundocreativo.flyshape.sim.AeroMetrics
import org.mundocreativo.flyshape.sim.FlowSolution
import org.mundocreativo.flyshape.sim.ParticleField
import org.mundocreativo.flyshape.sim.Streamline

private const val PARTICLE_COUNT = 240

/**
 * The working section: the painted flow, plus the instruments in the corners.
 *
 * The smoke is stepped by the frame clock in place, so no recomposition happens
 * per frame — only a repaint of this canvas.
 */
@Composable
internal fun TunnelViewport(
    state: WindTunnelState,
    solution: FlowSolution,
    metrics: AeroMetrics,
    streamlines: List<Streamline>,
    modifier: Modifier = Modifier,
) {
    val particles = remember { ParticleField(PARTICLE_COUNT) }
    val frame = remember { mutableIntStateOf(0) }
    val latestSolution by rememberUpdatedState(solution)

    LaunchedEffect(Unit) {
        particles.seed(latestSolution)
        var previous = 0L
        while (true) {
            withFrameMillis { now ->
                val deltaSeconds =
                    if (previous == 0L) 1f / 60f
                    else (now - previous).coerceIn(0L, 50L).toFloat() / 1000f
                previous = now
                if (state.running && state.showParticles) {
                    particles.advance(deltaSeconds, state.windSpeed, latestSolution)
                    frame.intValue++
                }
            }
        }
    }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // This read subscribes the draw pass to the animation clock, so the
            // smoke repaints every stepped frame. It is never negative.
            if (frame.intValue < 0) return@Canvas

            val camera = cameraFor(size.width, size.height)
            drawTunnelBackground()
            if (state.showGrid) drawTunnelGrid(camera)
            if (state.showPressure) drawPressureField(camera, solution)
            if (state.showStreamlines) drawStreamlines(camera, streamlines)
            if (state.showParticles) drawParticles(camera, particles)
            drawWing(camera, solution.contour)
            drawWindArrows(camera, solution.windDirection, state.windSpeed)
            drawForces(camera, solution, metrics)
        }

        ConditionsCard(
            state = state,
            modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
        )
        PerformanceCard(
            metrics = metrics,
            modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
        )
    }
}

@Composable
private fun InstrumentCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = Color(0xD90A141C),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, TunnelPalette.Accent.copy(alpha = 0.22f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = title,
                color = TunnelPalette.TextMuted,
                fontSize = 9.sp,
                letterSpacing = 1.6.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun ReadoutRow(
    label: String,
    value: String,
    valueColor: Color = TunnelPalette.TextPrimary,
) {
    Row(
        modifier = Modifier.widthIn(min = 138.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TunnelPalette.TextMuted, fontSize = 11.sp)
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ConditionsCard(state: WindTunnelState, modifier: Modifier = Modifier) {
    InstrumentCard(title = "AIRSTREAM", modifier = modifier) {
        ReadoutRow("Speed", "${state.windSpeed.asFixed(0)} m/s")
        ReadoutRow("Angle", "${state.windAngleDeg.asFixed(1)}°")
        ReadoutRow("Dyn. pressure", "${AeroMetrics.dynamicPressure(state.windSpeed).asFixed(0)} Pa")
    }
}

@Composable
private fun PerformanceCard(metrics: AeroMetrics, modifier: Modifier = Modifier) {
    InstrumentCard(title = "SECTION LOADS", modifier = modifier) {
        ReadoutRow("Cl", metrics.liftCoefficient.asFixed(2))
        ReadoutRow("Cd", metrics.dragCoefficient.asFixed(3))
        ReadoutRow("L / D", metrics.liftToDrag.asFixed(1))
        ReadoutRow(
            label = "Stall",
            value = if (metrics.stalled) "YES" else "no",
            valueColor = if (metrics.stalled) TunnelPalette.Drag else TunnelPalette.Lift,
        )
    }
}
