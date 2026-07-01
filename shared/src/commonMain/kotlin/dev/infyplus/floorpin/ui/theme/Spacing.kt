package dev.infyplus.floorpin.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/** Spacing scale from app.css (--space-1..12), in dp. */
object Space {
    val x1 = 4.dp
    val x2 = 8.dp
    val x3 = 12.dp
    val x4 = 16.dp
    val x5 = 20.dp
    val x6 = 24.dp
    val x8 = 32.dp
    val x12 = 48.dp
}

val LocalSpace = staticCompositionLocalOf { Space }
