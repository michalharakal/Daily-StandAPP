package de.jugda.javaland

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Daily-StandAPP",
    ) {
        App()
    }
}