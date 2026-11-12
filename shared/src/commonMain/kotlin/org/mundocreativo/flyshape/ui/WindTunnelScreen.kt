package org.mundocreativo.flyshape.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mundocreativo.flyshape.sim.AeroMetrics
import org.mundocreativo.flyshape.sim.FlowSolution
import org.mundocreativo.flyshape.sim.StreamlineTracer

private val TunnelColorScheme = darkColorScheme(
    primary = TunnelPalette.Accent,
    onPrimary = Color(0xFF04212A),
    secondary = TunnelPalette.Lift,
    onSecondary = Color(0xFF04210E),
    background = TunnelPalette.Background,
    onBackground = TunnelPalette.TextPrimary,
    surface = TunnelPalette.Panel,
    onSurface = TunnelPalette.TextPrimary,
    surfaceVariant = TunnelPalette.PanelHighlight,
    onSurfaceVariant = TunnelPalette.TextMuted,
    outline = Color(0xFF33475C),
)

/**
 * The whole application: a working section on the left, the control desk on the
 * right. The flow is re-solved only when the shape or the wind angle changes —
 * wind speed and the render layers are free to move.
 */
@Composable
fun WindTunnelScreen() {
    val state = remember { WindTunnelState() }
    val solution = remember(state.shape, state.windAngleDeg) {
        FlowSolution.solve(state.shape, state.windAngleDeg)
    }
    val metrics = remember(solution) { AeroMetrics.of(solution) }
    val streamlines = remember(solution, state.showStreamlines) {
        if (state.showStreamlines) StreamlineTracer.rake(solution) else emptyList()
    }

    MaterialTheme(colorScheme = TunnelColorScheme) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(TunnelPalette.Background),
        ) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                TitleBar(state)
                TunnelViewport(
                    state = state,
                    solution = solution,
                    metrics = metrics,
                    streamlines = streamlines,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp),
                )
                LegendBar()
            }
            ControlPanel(
                state = state,
                panelCount = solution.panelCount,
                modifier = Modifier.width(330.dp).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun TitleBar(state: WindTunnelState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "FLYSHAPE",
                color = TunnelPalette.Accent,
                fontSize = 10.sp,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Wing section wind tunnel",
                color = TunnelPalette.TextPrimary,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = "${state.shape.label} · 2-D section",
            color = TunnelPalette.TextMuted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun LegendBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(TunnelPalette.Suction, "suction")
        LegendItem(TunnelPalette.Pressure, "pressure")
        LegendItem(TunnelPalette.Lift, "lift")
        LegendItem(TunnelPalette.Drag, "drag")
        LegendItem(TunnelPalette.Particle, "smoke")
        Spacer(Modifier.weight(1f))
        Text(
            text = "Slide the wind to see the section work",
            color = TunnelPalette.TextMuted,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text = label, color = TunnelPalette.TextMuted, fontSize = 10.sp)
    }
}
