package dev.infyplus.floorpin.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.toArgb
import java.io.ByteArrayOutputStream

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

    return ByteArrayOutputStream().use { out ->
        bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        out.toByteArray()
    }
}
