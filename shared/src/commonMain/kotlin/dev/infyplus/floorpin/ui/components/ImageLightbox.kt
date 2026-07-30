package dev.infyplus.floorpin.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import dev.infyplus.floorpin.Config
import dev.infyplus.floorpin.db.Photo
import dev.infyplus.floorpin.ui.theme.White

/**
 * Resolves a photo to something Coil can render: the local bytes while it is still queued for
 * upload, otherwise the server `imageUrl`, otherwise `/files/<key>`.
 *
 * Returns `Any?` rather than `String?` because a pending photo has no URL — its bytes only exist on
 * this device. Coil's `model` is `Any?` and the app's loader keeps the default `ByteArrayFetcher`
 * (`App.kt` adds components rather than replacing them), so a `ByteArray` renders as-is.
 *
 * [bytesFor] is injected so this stays free of a database dependency; callers pass the repo lookup.
 */
fun photoImageModel(photo: Photo, bytesFor: (String) -> ByteArray?): Any? =
    if (photo.pending == 1L) bytesFor(photo.id)
    else photo.imageUrl ?: photo.imageKey?.let { "${Config.BASE_URL}/files/$it" }

/** URL-only resolution, for callers that genuinely need a URL string (the PDF export fetches over HTTP). */
fun photoImageUrl(photo: Photo): String? =
    photo.imageUrl ?: photo.imageKey?.let { "${Config.BASE_URL}/files/$it" }

/**
 * Fullscreen tap-to-dismiss image preview. No-op when [url] is null.
 * Pass [onDelete] to show a delete control in the top bar (e.g. for issue photos).
 */
@Composable
fun ImageLightbox(url: Any?, onDelete: (() -> Unit)? = null, onDismiss: () -> Unit) {
    if (url == null) return
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black.copy(alpha = 0.94f), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } }) {
                AsyncImage(
                    model = url,
                    contentDescription = "Photo preview",
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentScale = ContentScale.Fit,
                )
                if (onDelete != null) {
                    LightboxButton(AppIcons.Trash, "Delete photo", Modifier.align(Alignment.TopStart), onDelete)
                }
                LightboxButton(AppIcons.Close, "Close", Modifier.align(Alignment.TopEnd), onDismiss)
            }
        }
    }
}

@Composable
private fun LightboxButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.15f),
        modifier = modifier.padding(16.dp),
    ) {
        Icon(icon, desc, Modifier.size(40.dp).padding(9.dp), tint = White)
    }
}
