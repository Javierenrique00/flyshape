package org.mundocreativo.flyshape

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Flyshape · Wind Tunnel",
        state = rememberWindowState(width = 1360.dp, height = 880.dp),
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(1080, 720)
        }
        App()
    }
}
