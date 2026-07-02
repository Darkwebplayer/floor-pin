package dev.infyplus.floorpin.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import dev.infyplus.floorpin.ui.theme.Accent
import dev.infyplus.floorpin.ui.theme.Ink
import dev.infyplus.floorpin.ui.theme.White

data class DrawnStroke(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float,
)

data class PhotoMarkerResult(
    val bytes: ByteArray,
    val fileName: String,
)

/** Platform-specific: composites strokes onto image bytes, returns JPEG. */
internal expect fun flattenImageWithStrokes(
    imageBytes: ByteArray,
    displayWidth: Int,
    displayHeight: Int,
    strokes: List<DrawnStroke>,
): ByteArray

private val MARKER_COLORS = listOf(
    Color(0xFFE53935), // red
    Color(0xFF1E88E5), // blue
    Color(0xFF43A047), // green
    Color(0xFFFDD835), // yellow
    Color.White,
)

private const val DEFAULT_STROKE_WIDTH_DP = 5f

/**
 * Returns a launcher: call it with (bytes, fileName, onResult).
 * Shows a full-screen annotation dialog; onResult receives the
 * flattened bytes (or null if cancelled).
 */
@Composable
fun rememberPhotoMarker(): (bytes: ByteArray, fileName: String, onResult: (PhotoMarkerResult?) -> Unit) -> Unit {
    var dialogState by remember { mutableStateOf<MarkerDialogState?>(null) }

    if (dialogState != null) {
        val s = dialogState!!
        PhotoMarkerDialog(
            imageBytes = s.bytes,
            fileName = s.fileName,
            onDone = { flattened ->
                s.onResult(PhotoMarkerResult(flattened, s.fileName))
                dialogState = null
            },
            onCancel = {
                s.onResult(null)
                dialogState = null
            },
        )
    }

    return { bytes, fileName, onResult ->
        dialogState = MarkerDialogState(bytes, fileName, onResult)
    }
}

private data class MarkerDialogState(
    val bytes: ByteArray,
    val fileName: String,
    val onResult: (PhotoMarkerResult?) -> Unit,
)

@Composable
private fun PhotoMarkerDialog(
    imageBytes: ByteArray,
    fileName: String,
    onDone: (ByteArray) -> Unit,
    onCancel: () -> Unit,
) {
    val strokes = remember { mutableStateListOf<DrawnStroke>() }
    var currentStroke by remember { mutableStateOf<List<Offset>?>(null) }
    var selectedColor by remember { mutableStateOf(MARKER_COLORS[0]) }
    var displaySize by remember { mutableStateOf(IntSize.Zero) }
    val strokeWidthPx = with(LocalDensity.current) { DEFAULT_STROKE_WIDTH_DP.dp.toPx() }
    var isDrawing by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── top bar ──
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        onClick = onCancel,
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.15f),
                    ) {
                        Icon(AppIcons.Close, "Cancel", Modifier.size(24.dp), tint = White)
                    }
                    Text("Mark photo", style = MaterialTheme.typography.labelSmall, color = White)
                    Surface(
                        onClick = {
                            val result = if (strokes.isEmpty()) imageBytes
                            else flattenImageWithStrokes(imageBytes, displaySize.width, displaySize.height, strokes.toList())
                            onDone(result)
                        },
                        shape = CircleShape,
                        color = Accent,
                    ) {
                        Icon(AppIcons.Check, "Done", Modifier.size(24.dp), tint = White)
                    }
                }

                // ── drawing area ──
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AsyncImage(
                        model = imageBytes,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds,
                    )
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { displaySize = it }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentStroke = mutableListOf(offset)
                                        isDrawing = true
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val pts = currentStroke ?: return@detectDragGestures
                                        (pts as MutableList).add(change.position)
                                    },
                                    onDragEnd = {
                                        val pts = currentStroke
                                        if (pts != null && pts.size > 1) {
                                            strokes.add(DrawnStroke(pts.toList(), selectedColor, strokeWidthPx))
                                        }
                                        currentStroke = null
                                        isDrawing = false
                                    },
                                    onDragCancel = {
                                        currentStroke = null
                                        isDrawing = false
                                    },
                                )
                            },
                    ) {
                        strokes.forEach { stroke -> drawStroke(stroke) }
                        currentStroke?.let { pts ->
                            if (pts.size >= 2) {
                                drawStroke(DrawnStroke(pts, selectedColor, strokeWidthPx))
                            }
                        }
                    }
                }

                // ── bottom toolbar ──
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MARKER_COLORS.forEach { color ->
                        val selected = color == selectedColor
                        Surface(
                            onClick = { selectedColor = color },
                            shape = CircleShape,
                            color = color,
                            modifier = Modifier.size(if (selected) 36.dp else 28.dp),
                            border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, White) else null,
                        ) {}
                    }
                    // undo
                    Surface(
                        onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(AppIcons.Undo, "Undo", Modifier.size(18.dp), tint = White)
                        }
                    }
                    // clear all
                    Surface(
                        onClick = { strokes.clear() },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(AppIcons.Trash, "Clear all", Modifier.size(18.dp), tint = White)
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawStroke(stroke: DrawnStroke) {
    if (stroke.points.size < 2) return
    val path = Path().apply {
        moveTo(stroke.points[0].x, stroke.points[0].y)
        for (i in 1 until stroke.points.size) {
            lineTo(stroke.points[i].x, stroke.points[i].y)
        }
    }
    drawPath(
        path = path,
        color = stroke.color,
        style = Stroke(
            width = stroke.strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}
