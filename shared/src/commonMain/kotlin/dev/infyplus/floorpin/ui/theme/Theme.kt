package dev.infyplus.floorpin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

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

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
)

@Composable
fun FloorPinTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalFloorPinColors provides FloorPinColors(),
        LocalSpace provides Space,
    ) {
        MaterialTheme(
            colorScheme = AppColorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
