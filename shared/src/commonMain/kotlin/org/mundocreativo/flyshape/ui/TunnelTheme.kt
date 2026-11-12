package org.mundocreativo.flyshape.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import org.mundocreativo.flyshape.sim.TunnelDomain
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

/** Colours of the tunnel instrument panel. */
internal object TunnelPalette {
    val Background = Color(0xFF080D13)
    val Panel = Color(0xFF101A24)
    val PanelHighlight = Color(0xFF16232F)
    val GridLine = Color(0xFF9FC4E8)
    val Accent = Color(0xFF4CD0E0)
    val TextPrimary = Color(0xFFE4EFF7)
    val TextMuted = Color(0xFF7E93A8)

    val Suction = Color(0xFF4FC3F7)
    val Neutral = Color(0xFF93A9BF)
    val Pressure = Color(0xFFFF7043)

    val BodyTop = Color(0xFF5B7695)
    val BodyBottom = Color(0xFF16212D)
    val BodyEdge = Color(0xFFD3E7F7)

    val Lift = Color(0xFF6BE58F)
    val Drag = Color(0xFFFFC45C)

    val StreamSlow = Color(0xFF2F6FD0)
    val StreamCruise = Color(0xFF3FD0D8)
    val StreamFast = Color(0xFFDCF7C0)

    val Particle = Color(0xFFE8F6FF)
}

/** The deep blue-to-hot colour ramp a tunnel operator expects for pressure. */
internal fun pressureColor(cp: Float): Color {
    val t = ((cp + 2.2f) / 3.2f).coerceIn(0f, 1f)
    val base = if (t < 0.45f) {
        lerp(TunnelPalette.Suction, TunnelPalette.Neutral, t / 0.45f)
    } else {
        lerp(TunnelPalette.Neutral, TunnelPalette.Pressure, (t - 0.45f) / 0.55f)
    }
    val strength = sqrt((abs(cp) / 2.2f).coerceIn(0f, 1f))
    return base.copy(alpha = 0.05f + 0.55f * strength)
}

/** Streamlines get brighter the faster the air is moving there. */
internal fun speedColor(speed: Float): Color {
    val t = (speed / 1.9f).coerceIn(0f, 1f)
    val base = if (t < 0.5f) {
        lerp(TunnelPalette.StreamSlow, TunnelPalette.StreamCruise, t * 2f)
    } else {
        lerp(TunnelPalette.StreamCruise, TunnelPalette.StreamFast, (t - 0.5f) * 2f)
    }
    return base.copy(alpha = 0.32f + 0.40f * t)
}

/** Maps chord units onto pixels, keeping the aspect ratio of the tunnel. */
internal class TunnelCamera(val scale: Float, private val originX: Float, private val originY: Float) {

    fun x(worldX: Float): Float = originX + worldX * scale

    fun y(worldY: Float): Float = originY - worldY * scale

    fun length(worldLength: Float): Float = worldLength * scale
}

internal fun cameraFor(width: Float, height: Float): TunnelCamera {
    val scale = min(width / TunnelDomain.WIDTH, height / TunnelDomain.HEIGHT)
    return TunnelCamera(
        scale = scale,
        originX = width / 2f - TunnelDomain.center.x * scale,
        originY = height / 2f + TunnelDomain.center.y * scale,
    )
}

/** Fixed-point formatting; `String.format` is not available in common code. */
internal fun Float.asFixed(decimals: Int): String {
    if (!isFinite()) return "–"
    val factor = 10.0.pow(decimals).toLong()
    val scaled = round(toDouble() * factor).toLong()
    val sign = if (scaled < 0) "-" else ""
    val magnitude = abs(scaled)
    val whole = magnitude / factor
    if (decimals == 0) return "$sign$whole"
    val fraction = magnitude % factor
    return "$sign$whole.${fraction.toString().padStart(decimals, '0')}"
}
