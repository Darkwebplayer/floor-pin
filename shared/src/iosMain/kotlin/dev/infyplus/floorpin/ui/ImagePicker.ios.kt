package dev.infyplus.floorpin.ui

import androidx.compose.runtime.Composable

// Stub: image picking / camera are out of v1 iOS scope.
@Composable
actual fun rememberImagePicker(onImage: (bytes: ByteArray, fileName: String) -> Unit): () -> Unit = {}

@Composable
actual fun rememberCameraCapture(onImage: (bytes: ByteArray, fileName: String) -> Unit): () -> Unit = {}
