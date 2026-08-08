package dev.infyplus.floorpin.ui

import androidx.compose.runtime.Composable

/** Returns a launcher; calling it opens the system photo picker and yields downscaled WebP bytes.
 *  [multiple] lets the user select several at once; the callback fires once with all of them. */
@Composable
expect fun rememberImagePicker(
    multiple: Boolean = false,
    onImages: (images: List<Pair<ByteArray, String>>) -> Unit,
): () -> Unit

/** Returns a launcher; calling it opens the camera and yields a downscaled JPEG of the capture. */
@Composable
expect fun rememberCameraCapture(onImage: (bytes: ByteArray, fileName: String) -> Unit): () -> Unit
