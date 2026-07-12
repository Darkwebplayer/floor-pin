package dev.infyplus.floorpin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** App-wide dark-mode override. Experimental toggle from the sidebar. */
val DarkModeOverride = mutableStateOf(true)

private val AppColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = White,
    primaryContainer = Accent,
    onPrimaryContainer = White,
    secondary = Ink2,
    onSecondary = White,
    background = SurfaceWarm,
    onBackground = Ink,
    surface = White,
    onSurface = Ink,
    surfaceVariant = SurfaceWarm,
    onSurfaceVariant = Muted,
    outline = BorderColor,
    outlineVariant = BorderColor,
    error = Danger,
    onError = White,
)

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = White,
    primaryContainer = Accent,
    onPrimaryContainer = White,
    secondary = Ink2,
    onSecondary = White,
    background = Color(0xFF0E1420),
    onBackground = White,
    surface = Color(0xFF161D2B),
    onSurface = White,
    surfaceVariant = Color(0xFF1E2636),
    onSurfaceVariant = Color(0xFFB4C0D4),
    outline = Color(0xFF39445A),
    outlineVariant = Color(0xFF39445A),
    error = Danger,
    onError = White,
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
)

@Composable
fun FloorPinTheme(content: @Composable () -> Unit) {
    val dark by DarkModeOverride
    CompositionLocalProvider(
        LocalFloorPinColors provides FloorPinColors(),
        LocalSpace provides Space,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColorScheme else AppColorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
