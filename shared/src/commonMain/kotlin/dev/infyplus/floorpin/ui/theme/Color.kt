package dev.infyplus.floorpin.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Raw tokens from assets/app.css :root ──
val Ink = Color(0xFF061B31)        // --fg
val Ink2 = Color(0xFF273951)       // --fg-2
val Muted = Color(0xFF64748D)      // --muted
val BorderColor = Color(0xFFE5EDF5) // --border
val SurfaceWarm = Color(0xFFF6F9FC) // --surface-warm
val White = Color(0xFFFFFFFF)

val Accent = Color(0xFF533AFD)
val AccentHover = Color(0xFF4434D4)
val AccentActive = Color(0xFF2E2B8C)

val Success = Color(0xFF15BE53)
val Warn = Color(0xFF9B6829)
val Danger = Color(0xFFEA2261)

// status hues for pins/badges
val StatusOpen = Danger
val StatusProgress = Warn
val StatusResolved = Success
val PinLocation = Accent

/** Extra brand colors Material3's ColorScheme doesn't model cleanly. */
@Immutable
data class FloorPinColors(
    val ink: Color = Ink,
    val ink2: Color = Ink2,
    val muted: Color = Muted,
    val border: Color = BorderColor,
    val surfaceWarm: Color = SurfaceWarm,
    val accent: Color = Accent,
    val statusOpen: Color = StatusOpen,
    val statusProgress: Color = StatusProgress,
    val statusResolved: Color = StatusResolved,
)

val LocalFloorPinColors = staticCompositionLocalOf { FloorPinColors() }
