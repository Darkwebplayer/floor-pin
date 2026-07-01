package dev.infyplus.floorpin.ui

import androidx.compose.runtime.Composable

/** Returns a launcher; calling it opens the system photo picker and yields downscaled JPEG bytes. */
@Composable
expect fun rememberImagePicker(onImage: (bytes: ByteArray, fileName: String) -> Unit): () -> Unit

/** Returns a launcher; calling it opens the camera and yields a downscaled JPEG of the capture. */
@Composable
expect fun rememberCameraCapture(onImage: (bytes: ByteArray, fileName: String) -> Unit): () -> Unit
