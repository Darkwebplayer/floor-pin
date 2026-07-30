package dev.infyplus.floorpin.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import java.io.ByteArrayOutputStream

/** Longest edge kept for uploads — enough for the in-app fullscreen lightbox. */
internal const val MAX_EDGE = 1600

/** WebP quality for uploads. ~q78 WebP tracks q85 JPEG, at roughly 30% fewer bytes. */
internal const val IMAGE_QUALITY = 78

private fun Bitmap.encode(format: Bitmap.CompressFormat, quality: Int): ByteArray? =
    ByteArrayOutputStream().use { out ->
        // compress() reports failure by returning false and writing nothing — it does not throw.
        if (compress(format, quality, out)) out.toByteArray().takeIf { it.isNotEmpty() } else null
    }

/** WebP lossy is ~30% smaller than JPEG at matching quality. `WEBP_LOSSY` is API 30+;
 *  below that the deprecated `WEBP` enum is the same encoder (lossy whenever quality < 100).
 *
 *  Falls back to JPEG rather than returning the empty array an unchecked `compress()` would yield:
 *  some bitmap configs (ALPHA_8, HARDWARE) have no WebP encoder, and a zero-byte upload reaches the
 *  server as a corrupt file. Callers label the part by sniffing the bytes, so the fallback can't
 *  mislabel the stored object. */
@Suppress("DEPRECATION")
internal fun Bitmap.compressWebp(quality: Int): ByteArray {
    val webp =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
        else Bitmap.CompressFormat.WEBP
    return encode(webp, quality)
        ?: encode(Bitmap.CompressFormat.JPEG, quality)
        ?: error("Could not encode ${width}x$height bitmap ($config)")
}

/** Decode [bytes] with the longest edge at most [maxEdge] px. Sub-samples during decode so a
 *  12MP capture never lands in memory at full size, then scales the remainder exactly. */
internal fun decodeScaled(bytes: ByteArray, maxEdge: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    while (longest / sample > maxEdge * 2) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return null
    val edge = maxOf(decoded.width, decoded.height)
    if (edge <= maxEdge) return decoded
    val r = maxEdge.toFloat() / edge
    // coerceAtLeast(1): a very long thin image (e.g. 4000x2) scales its short side to 0,
    // and createScaledBitmap throws on a zero dimension.
    val scaled = Bitmap.createScaledBitmap(
        decoded,
        (decoded.width * r).toInt().coerceAtLeast(1),
        (decoded.height * r).toInt().coerceAtLeast(1),
        true,
    )
    if (scaled !== decoded) decoded.recycle()
    return scaled
}

internal actual fun downscaleImage(imageBytes: ByteArray, maxEdge: Int, quality: Int): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    if (longest <= 0 || longest <= maxEdge) return imageBytes // already small enough; don't re-encode
    val bmp = decodeScaled(imageBytes, maxEdge) ?: return imageBytes
    return bmp.compressWebp(quality).also { bmp.recycle() }
}
