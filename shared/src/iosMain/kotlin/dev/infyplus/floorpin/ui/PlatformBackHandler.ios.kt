package dev.infyplus.floorpin.ui

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) { /* iOS: system back via gestures */ }
