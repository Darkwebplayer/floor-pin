package dev.infyplus.floorpin.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.toArgb

internal actual fun flattenImageWithStrokes(
    imageBytes: ByteArray,
    displayWidth: Int,
    displayHeight: Int,
    strokes: List<DrawnStroke>,
): ByteArray {
    if (displayWidth <= 0 || displayHeight <= 0 || strokes.isEmpty()) return imageBytes

    val src = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        ?: return imageBytes
    val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
    if (src !== bmp) src.recycle()

    val canvas = android.graphics.Canvas(bmp)
    val sx = bmp.width.toFloat() / displayWidth
    val sy = bmp.height.toFloat() / displayHeight

    strokes.forEach { stroke ->
        if (stroke.points.size < 2) return@forEach
        val paint = Paint().apply {
            color = stroke.color.toArgb()
            strokeWidth = stroke.strokeWidth * maxOf(sx, sy)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        val path = Path()
        val pts = stroke.points
        path.moveTo(pts[0].x * sx, pts[0].y * sy)
        for (i in 1 until pts.size) {
            path.lineTo(pts[i].x * sx, pts[i].y * sy)
        }
        canvas.drawPath(path, paint)
    }

    // Re-encode at the same quality as the original upload — annotating a photo shouldn't grow it.
    return bmp.compressWebp(IMAGE_QUALITY).also { bmp.recycle() }
}

internal actual fun rotateImage(imageBytes: ByteArray, degrees: Int): ByteArray {
    val src = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return imageBytes
    val rotated = Bitmap.createBitmap(
        src, 0, 0, src.width, src.height,
        Matrix().apply { postRotate(degrees.toFloat()) }, true,
    )
    if (src !== rotated) src.recycle()
    return rotated.compressWebp(IMAGE_QUALITY).also { rotated.recycle() }
}
