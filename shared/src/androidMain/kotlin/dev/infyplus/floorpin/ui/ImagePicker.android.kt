package dev.infyplus.floorpin.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import dev.infyplus.floorpin.data.nowMillis
import dev.infyplus.floorpin.ui.components.IMAGE_QUALITY
import dev.infyplus.floorpin.ui.components.MAX_EDGE
import dev.infyplus.floorpin.ui.components.compressWebp
import dev.infyplus.floorpin.ui.components.decodeScaled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File

/** Rotate/flip [bmp] per the source EXIF orientation. BitmapFactory ignores EXIF, so
 *  without this, photos from devices that store rotation as metadata come out sideways. */
private fun applyExifOrientation(raw: ByteArray, bmp: Bitmap): Bitmap {
    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(raw))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val m = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
        else -> return bmp
    }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
}

/** Decode → downscale longest edge to MAX_EDGE → apply EXIF orientation → WebP. Keeps uploads small.
 *  Any failure falls back to the original bytes: a larger upload beats a failed or corrupt one. */
private fun processImage(raw: ByteArray): ByteArray = runCatching {
    val decoded = decodeScaled(raw, MAX_EDGE) ?: return raw
    val bmp = applyExifOrientation(raw, decoded)
    if (bmp !== decoded) decoded.recycle()
    bmp.compressWebp(IMAGE_QUALITY).also { bmp.recycle() }
}.getOrDefault(raw)

@Composable
actual fun rememberImagePicker(onImage: (bytes: ByteArray, fileName: String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { processImage(it.readBytes()) }
            } ?: return@launch
            onImage(bytes, "upload_${nowMillis()}.webp")
        }
    }
    return { launcher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
}

@Composable
actual fun rememberCameraCapture(onImage: (bytes: ByteArray, fileName: String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Reuse one temp file in cache; the camera app writes the full-res capture here.
    val tempFile = remember { File(context.cacheDir, "camera_capture.jpg") }
    val uri = remember {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
    }
    val launcher = rememberLauncherForActivityResult(TakePicture()) { ok ->
        if (!ok) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { processImage(tempFile.readBytes()) }.getOrNull()
            } ?: return@launch
            onImage(bytes, "capture_${nowMillis()}.webp")
        }
    }
    return { launcher.launch(uri) }
}
