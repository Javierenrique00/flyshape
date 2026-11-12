package org.mundocreativo.flyshape.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mundocreativo.flyshape.sim.AeroMetrics
import org.mundocreativo.flyshape.sim.WingShape

/** Everything the operator can change, in one scrollable column. */
@Composable
internal fun ControlPanel(
    state: WindTunnelState,
    panelCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(TunnelPalette.Panel)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Column {
            Text(
                text = "CONTROL DESK",
                color = TunnelPalette.Accent,
                fontSize = 9.sp,
                letterSpacing = 2.6.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Wind tunnel",
                color = TunnelPalette.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        SliderSection(
            label = "Wind speed",
            valueText = "${state.windSpeed.asFixed(0)} m/s",
        ) {
            Slider(
                value = state.windSpeed,
                onValueChange = { state.windSpeed = it },
                valueRange = WindTunnelState.MIN_SPEED..WindTunnelState.MAX_SPEED,
            )
        }

        SliderSection(
            label = "Wind angle",
            valueText = "${state.windAngleDeg.asFixed(1)}°",
        ) {
            Slider(
                value = state.windAngleDeg,
                onValueChange = { state.windAngleDeg = it },
                valueRange = WindTunnelState.MIN_ANGLE..WindTunnelState.MAX_ANGLE,
            )
        }

        ShapePicker(state)
        LayerToggles(state)
        TransportButtons(state)
        Footnote(panelCount)
    }
}

@Composable
private fun SliderSection(
    label: String,
    valueText: String,
    content: @Composable () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = TunnelPalette.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = valueText,
                color = TunnelPalette.Accent,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(2.dp))
        content()
    }
}

@Composable
private fun ShapePicker(state: WindTunnelState) {
    Column {
        SectionLabel("WING SECTION")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WingShape.entries.forEach { shape ->
                SelectableRow(
                    label = shape.label,
                    selected = state.shape == shape,
                    onClick = { state.shape = shape },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.shape.description,
            color = TunnelPalette.TextMuted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun SelectableRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val accent = if (selected) TunnelPalette.Accent else Color(0xFF3A4C5E)
    val background = if (selected) {
        TunnelPalette.Accent.copy(alpha = 0.16f)
    } else {
        TunnelPalette.PanelHighlight
    }
    val shape = RoundedCornerShape(8.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.55f)), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(8.dp).background(accent, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = if (selected) TunnelPalette.TextPrimary else TunnelPalette.TextMuted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun LayerToggles(state: WindTunnelState) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionLabel("LAYERS")
        Spacer(Modifier.height(2.dp))
        ToggleRow("Pressure map", state.showPressure) { state.showPressure = it }
        ToggleRow("Streamlines", state.showStreamlines) { state.showStreamlines = it }
        ToggleRow("Smoke particles", state.showParticles) { state.showParticles = it }
        ToggleRow("Reference grid", state.showGrid) { state.showGrid = it }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = TunnelPalette.TextPrimary, fontSize = 12.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun TransportButtons(state: WindTunnelState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = { state.running = !state.running },
            modifier = Modifier.weight(1f),
        ) {
            Text(if (state.running) "Pause" else "Resume", fontSize = 12.sp)
        }
        Button(
            onClick = state::reset,
            modifier = Modifier.weight(1f),
        ) {
            Text("Reset", fontSize = 12.sp)
        }
    }
}

@Composable
private fun Footnote(panelCount: Int) {
    Text(
        text = "Inviscid Hess–Smith panel method, $panelCount panels. The painted " +
            "flow is potential flow; Cl, Cd and L/D are corrected for a finite wing " +
            "and for separation past ${AeroMetrics.STALL_ANGLE_DEG}°.",
        color = TunnelPalette.TextMuted,
        fontSize = 10.sp,
        lineHeight = 14.sp,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = TunnelPalette.TextMuted,
        fontSize = 10.sp,
        letterSpacing = 1.4.sp,
        fontWeight = FontWeight.SemiBold,
    )
}
