package dev.infyplus.floorpin.ui.screens.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.rememberAsyncImagePainter
import androidx.compose.foundation.Image
import dev.infyplus.floorpin.AppContainer
import dev.infyplus.floorpin.db.Issue
import dev.infyplus.floorpin.db.Location
import dev.infyplus.floorpin.domain.IssueStatus
import dev.infyplus.floorpin.domain.worstStatus
import dev.infyplus.floorpin.ui.components.AppIcons
import dev.infyplus.floorpin.ui.components.AppTopBar
import dev.infyplus.floorpin.ui.screens.floorPlanImageUrl
import dev.infyplus.floorpin.ui.theme.Accent
import dev.infyplus.floorpin.ui.theme.BorderColor
import dev.infyplus.floorpin.ui.theme.Ink
import dev.infyplus.floorpin.ui.theme.LocalFloorPinColors
import dev.infyplus.floorpin.ui.theme.Muted
import dev.infyplus.floorpin.ui.theme.SurfaceWarm
import dev.infyplus.floorpin.ui.theme.White
import kotlin.math.min

enum class ViewerMode { Browse, AddLocation, AddIssue, Move }

@Composable
fun pinColorFor(statuses: List<IssueStatus>): Color {
    val fp = LocalFloorPinColors.current
    return when (worstStatus(statuses)) {
        null -> fp.accent
        IssueStatus.OPEN -> fp.statusOpen
        IssueStatus.IN_PROGRESS -> fp.statusProgress
        IssueStatus.RESOLVED -> fp.statusResolved
        IssueStatus.CLOSED -> fp.muted
    }
}

@Composable
fun ViewerScreen(
    container: AppContainer,
    floorPlanId: String,
    floorPlanName: String,
    onBack: () -> Unit,
    onOpenReport: () -> Unit,
) {
    val vm: ViewerViewModel = viewModel(key = floorPlanId) { ViewerViewModel(container, floorPlanId) }
    val locations by vm.locations.collectAsStateWithLifecycle()
    val issues by vm.issues.collectAsStateWithLifecycle()

    val issuesByLoc = remember(issues) { issues.groupBy { it.locationId } }
    var mode by remember { mutableStateOf(ViewerMode.Browse) }
    var selectedLocId by remember { mutableStateOf<String?>(null) }
    var selectedIssueId by remember { mutableStateOf<String?>(null) }
    var pinsHidden by remember { mutableStateOf(false) }
    var placingIssueLoc by remember { mutableStateOf<String?>(null) }
    var addIssueTarget by remember { mutableStateOf<Triple<String, Double, Double>?>(null) }

    val imageUrl = vm.floorPlan?.let { floorPlanImageUrl(it) }

    Column(Modifier.fillMaxSize().background(SurfaceWarm)) {
        AppTopBar(title = floorPlanName, crumb = "Floor Plans", onBack = onBack) {
            dev.infyplus.floorpin.ui.components.AppButton("Report", onClick = onOpenReport, small = true)
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 980.dp
            val density = LocalDensity.current
            val vw = constraints.maxWidth.toFloat()
            val vh = constraints.maxHeight.toFloat()
            val panelPx = with(density) { 372.dp.toPx() }
            val canvasW = if (wide) (vw - panelPx) else vw
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    PlanCanvas(
                        imageUrl = imageUrl,
                        widthPx = canvasW,
                        heightPx = vh,
                        aspect = 1.4f,
                        locations = if (pinsHidden) emptyList() else locations,
                        issues = if (pinsHidden) emptyList() else issues,
                        issuesByLoc = issuesByLoc,
                        mode = mode,
                        placingIssue = placingIssueLoc != null,
                        // highlight the pin being targeted for an issue without opening the inspector
                        selectedLocId = selectedLocId ?: placingIssueLoc,
                        onTapEmpty = { selectedLocId = null; selectedIssueId = null },
                        onTapLocation = {
                            if (mode == ViewerMode.AddIssue) {
                                placingIssueLoc = it // select for placement only — no details popup
                            } else {
                                selectedLocId = it; selectedIssueId = null
                            }
                        },
                        onTapIssue = { issueId, locId -> selectedLocId = locId; selectedIssueId = issueId },
                        onMoveIssue = { id, x, y -> vm.moveIssue(id, x, y) },
                        onAddLocation = { x, y ->
                            val loc = vm.addLocation(x, y)
                            mode = ViewerMode.Browse
                            selectedLocId = loc.id
                        },
                        onPlaceIssue = { x, y ->
                            placingIssueLoc?.let { loc -> addIssueTarget = Triple(loc, x, y) }
                            placingIssueLoc = null
                            mode = ViewerMode.Browse
                        },
                        onMoveLocation = { id, x, y -> vm.moveLocation(id, x, y) },
                    )
                    // mode chip
                    if (mode != ViewerMode.Browse) {
                        Surface(
                            color = Ink, contentColor = White, shape = RoundedCornerShape(99.dp),
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                        ) {
                            Text(
                                when (mode) {
                                    ViewerMode.AddLocation -> "Tap the plan to drop a location"
                                    ViewerMode.AddIssue ->
                                        if (placingIssueLoc != null) "Tap to drop the issue pin"
                                        else "Tap a pin, then tap to place the issue"
                                    else -> "Drag pins to reposition"
                                },
                                Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Toolbar(
                        mode = mode, pinsHidden = pinsHidden,
                        onMode = { mode = if (mode == it) ViewerMode.Browse else it },
                        onToggleHide = { pinsHidden = !pinsHidden },
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                    )
                    Legend(Modifier.align(Alignment.BottomStart).padding(16.dp))
                }
                if (wide) {
                    Inspector(
                        vm = vm,
                        location = locations.firstOrNull { it.id == selectedLocId },
                        issues = selectedLocId?.let { issuesByLoc[it] } ?: emptyList(),
                        selectedIssueId = selectedIssueId,
                        onSelectIssue = { selectedIssueId = it },
                        onClose = { selectedLocId = null; selectedIssueId = null },
                        modifier = Modifier.width(372.dp).fillMaxHeight(),
                    )
                }
            }

            // narrow: bottom-sheet-style inspector overlay
            if (!wide && selectedLocId != null) {
                Inspector(
                    vm = vm,
                    location = locations.firstOrNull { it.id == selectedLocId },
                    issues = selectedLocId?.let { issuesByLoc[it] } ?: emptyList(),
                    selectedIssueId = selectedIssueId,
                    onSelectIssue = { selectedIssueId = it },
                    onClose = { selectedLocId = null; selectedIssueId = null },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .fillMaxHeight(0.85f),
                )
            }
        }

        // Add-issue tool: after placing the pin, collect details + optional photo.
        addIssueTarget?.let { (locId, ix, iy) ->
            AddIssueDialog(
                onDismiss = { addIssueTarget = null },
                onCreate = { title, desc, status, priority, type, category, item, photoBytes, photoName ->
                    vm.addIssueWithPhoto(locId, title, desc, status, priority, type, category, item, ix, iy, photoBytes, photoName)
                    addIssueTarget = null
                },
            )
        }
    }
}

@Composable
private fun PlanCanvas(
    imageUrl: String?,
    widthPx: Float,
    heightPx: Float,
    aspect: Float,
    locations: List<Location>,
    issues: List<Issue>,
    issuesByLoc: Map<String, List<Issue>>,
    mode: ViewerMode,
    placingIssue: Boolean,
    selectedLocId: String?,
    onTapEmpty: () -> Unit,
    onTapLocation: (String) -> Unit,
    onTapIssue: (issueId: String, locationId: String) -> Unit,
    onAddLocation: (Double, Double) -> Unit,
    onPlaceIssue: (Double, Double) -> Unit,
    onMoveLocation: (String, Double, Double) -> Unit,
    onMoveIssue: (String, Double, Double) -> Unit,
) {
    val density = LocalDensity.current
    val painter = imageUrl?.let { rememberAsyncImagePainter(it) }
    val intrinsic = painter?.intrinsicSize
    val ar = if (intrinsic != null && intrinsic.isSpecified && intrinsic.height > 0) intrinsic.width / intrinsic.height else aspect

    // fit base size into the viewport
    val pad = with(density) { 32.dp.toPx() }
    var baseW = (widthPx - pad).coerceAtLeast(1f)
    var baseH = baseW / ar
    if (baseH > heightPx - pad) { baseH = (heightPx - pad).coerceAtLeast(1f); baseW = baseH * ar }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var inited by remember { mutableStateOf(false) }
    if (!inited && widthPx > 1f && heightPx > 1f) {
        offset = Offset((widthPx - baseW) / 2f, (heightPx - baseH) / 2f)
        inited = true
    }

    fun toFraction(p: Offset): Pair<Double, Double> {
        val lx = (p.x - offset.x) / scale
        val ly = (p.y - offset.y) / scale
        val fx = (lx / baseW * 100f).toDouble().coerceIn(1.0, 99.0)
        val fy = (ly / baseH * 100f).toDouble().coerceIn(1.0, 99.0)
        return fx to fy
    }

    Box(
        Modifier.fillMaxSize().background(SurfaceWarm)
            // Pan/zoom is off in Move mode so dragging a pin/issue doesn't fight the canvas.
            .then(
                if (mode == ViewerMode.Move) Modifier
                else Modifier.pointerInput(mode, baseW, baseH) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(0.5f, 6f)
                        val k = newScale / scale
                        offset = Offset(
                            centroid.x - (centroid.x - offset.x) * k,
                            centroid.y - (centroid.y - offset.y) * k,
                        ) + pan
                        scale = newScale
                    }
                }
            )
            .pointerInput(mode, placingIssue, baseW, baseH) {
                detectTapGestures { pos ->
                    val (fx, fy) = toFraction(pos)
                    when {
                        mode == ViewerMode.AddLocation -> onAddLocation(fx, fy)
                        mode == ViewerMode.AddIssue && placingIssue -> onPlaceIssue(fx, fy)
                        else -> onTapEmpty()
                    }
                }
            },
    ) {
        Box(
            Modifier
                .size(with(density) { baseW.toDp() }, with(density) { baseH.toDp() })
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = offset.x; translationY = offset.y
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        ) {
            if (painter != null) {
                Image(painter, contentDescription = "Floor plan", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            }
            locations.forEach { loc ->
                val statuses = (issuesByLoc[loc.id] ?: emptyList()).map { IssueStatus.fromWire(it.status) }
                PinMarker(
                    loc = loc,
                    count = issuesByLoc[loc.id]?.size ?: 0,
                    color = pinColorFor(statuses),
                    selected = loc.id == selectedLocId,
                    baseWDp = with(density) { baseW.toDp() },
                    baseHDp = with(density) { baseH.toDp() },
                    invScale = 1f / scale,
                    draggable = mode == ViewerMode.Move,
                    onClick = { onTapLocation(loc.id) },
                    // fired once on drag-end with the total screen-px delta — one write, one sync
                    onDragEnd = { dxPx, dyPx ->
                        val nx = (loc.x + dxPx / scale / baseW * 100).coerceIn(1.0, 99.0)
                        val ny = (loc.y + dyPx / scale / baseH * 100).coerceIn(1.0, 99.0)
                        onMoveLocation(loc.id, nx, ny)
                    },
                )
            }
            // issue dots (always visible, colored by status; draggable in Move mode)
            issues.forEach { issue ->
                val ix = issue.x ?: return@forEach
                val iy = issue.y ?: return@forEach
                IssueDot(
                    id = issue.id,
                    xDp = with(density) { baseW.toDp() } * (ix.toFloat() / 100f),
                    yDp = with(density) { baseH.toDp() } * (iy.toFloat() / 100f),
                    color = pinColorFor(listOf(IssueStatus.fromWire(issue.status))),
                    invScale = 1f / scale,
                    draggable = mode == ViewerMode.Move,
                    onClick = { onTapIssue(issue.id, issue.locationId) },
                    onDragEnd = { dxPx, dyPx ->
                        val nx = (ix + dxPx / scale / baseW * 100).coerceIn(1.0, 99.0)
                        val ny = (iy + dyPx / scale / baseH * 100).coerceIn(1.0, 99.0)
                        onMoveIssue(issue.id, nx, ny)
                    },
                )
            }
        }

        ZoomControls(
            onIn = { scale = (scale * 1.25f).coerceAtMost(6f) },
            onOut = { scale = (scale * 0.8f).coerceAtLeast(0.5f) },
            onFit = { scale = 1f; offset = Offset((widthPx - baseW) / 2f, (heightPx - baseH) / 2f) },
            scale = scale,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun PinMarker(
    loc: Location,
    count: Int,
    color: Color,
    selected: Boolean,
    baseWDp: androidx.compose.ui.unit.Dp,
    baseHDp: androidx.compose.ui.unit.Dp,
    invScale: Float,
    draggable: Boolean,
    onClick: () -> Unit,
    onDragEnd: (Float, Float) -> Unit,
) {
    val density = LocalDensity.current
    // live drag offset (screen px), applied locally for smooth motion; committed once on release
    var dragPx by remember(loc.id, draggable) { mutableStateOf(Offset.Zero) }
    val xDp = baseWDp * (loc.x.toFloat() / 100f)
    val yDp = baseHDp * (loc.y.toFloat() / 100f)
    Box(
        Modifier
            .offset(
                x = xDp - 15.dp + with(density) { (dragPx.x * invScale).toDp() },
                y = yDp - 36.dp + with(density) { (dragPx.y * invScale).toDp() },
            )
            .graphicsLayer { scaleX = invScale; scaleY = invScale; transformOrigin = TransformOrigin(0.5f, 1f) }
            .size(width = 30.dp, height = 36.dp)
            .then(
                if (draggable) Modifier.pointerInput(loc.id) {
                    detectDragGestures(
                        onDragEnd = { onDragEnd(dragPx.x, dragPx.y); dragPx = Offset.Zero },
                        onDragCancel = { dragPx = Offset.Zero },
                    ) { change, drag -> change.consume(); dragPx += drag }
                } else Modifier.pointerInput(loc.id) { detectTapGestures { onClick() } },
        ),
        contentAlignment = Alignment.TopCenter,
    ) {
        val border = if (selected) Accent else White
        val borderW = if (selected) 3.dp else 2.dp
        // teardrop map pin: circle head unioned with a pointed tip, single clean outline
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val r = w / 2f
            val head = Path().apply { addOval(Rect(0f, 0f, w, w)) }
            val tail = Path().apply {
                moveTo(r - r * 0.62f, w * 0.72f)
                lineTo(r, h)
                lineTo(r + r * 0.62f, w * 0.72f)
                close()
            }
            val pin = Path().apply { op(head, tail, PathOperation.Union) }
            drawPath(pin, color)
            drawPath(pin, border, style = Stroke(width = borderW.toPx()))
        }
        if (count > 0) {
            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                Text("$count", color = White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
        }
        // name label under the pin tip (overflows the 30dp box, centered on it)
        Box(Modifier.offset(y = 38.dp).width(120.dp), contentAlignment = Alignment.TopCenter) {
            Surface(color = White.copy(alpha = 0.9f), shape = RoundedCornerShape(4.dp)) {
                Text(
                    loc.name,
                    color = Ink,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun IssueDot(
    id: String,
    xDp: androidx.compose.ui.unit.Dp,
    yDp: androidx.compose.ui.unit.Dp,
    color: Color,
    invScale: Float,
    draggable: Boolean,
    onClick: () -> Unit,
    onDragEnd: (Float, Float) -> Unit,
) {
    val density = LocalDensity.current
    var dragPx by remember(id, draggable) { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .offset(
                x = xDp - 7.dp + with(density) { (dragPx.x * invScale).toDp() },
                y = yDp - 7.dp + with(density) { (dragPx.y * invScale).toDp() },
            )
            .graphicsLayer { scaleX = invScale; scaleY = invScale; transformOrigin = TransformOrigin(0.5f, 0.5f) }
            .size(14.dp)
            .then(
                if (draggable) Modifier.pointerInput(id) {
                    detectDragGestures(
                        onDragEnd = { onDragEnd(dragPx.x, dragPx.y); dragPx = Offset.Zero },
                        onDragCancel = { dragPx = Offset.Zero },
                    ) { change, drag -> change.consume(); dragPx += drag }
                } else Modifier.pointerInput(id) { detectTapGestures { onClick() } }
            )
            .background(color, CircleShape)
            .border(2.dp, White, CircleShape),
    )
}

@Composable
private fun Toolbar(
    mode: ViewerMode,
    pinsHidden: Boolean,
    onMode: (ViewerMode) -> Unit,
    onToggleHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(shape = RoundedCornerShape(6.dp), color = White, border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor), shadowElevation = 2.dp, modifier = modifier) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ToolButton(AppIcons.Map, mode == ViewerMode.Browse) { onMode(ViewerMode.Browse) }
            ToolButton(AppIcons.LocationAdd, mode == ViewerMode.AddLocation) { onMode(ViewerMode.AddLocation) }
            ToolButton(AppIcons.DefectAdd, mode == ViewerMode.AddIssue) { onMode(ViewerMode.AddIssue) }
            ToolButton(AppIcons.Move, mode == ViewerMode.Move) { onMode(ViewerMode.Move) }
            ToolButton(if (pinsHidden) AppIcons.EyeOff else AppIcons.Eye, pinsHidden) { onToggleHide() }
        }
    }
}

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(4.dp), color = if (on) Accent else White, contentColor = if (on) White else Ink, modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(21.dp)) }
    }
}

@Composable
private fun ZoomControls(onIn: () -> Unit, onOut: () -> Unit, onFit: () -> Unit, scale: Float, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(6.dp), color = White, border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor), shadowElevation = 2.dp, modifier = modifier) {
        Column {
            ZoomBtn("+") { onIn() }
            ZoomBtn("−") { onOut() }
            Surface(onClick = onFit, color = White, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(AppIcons.FitScreen, "Fit", Modifier.size(18.dp), tint = Ink) }
            }
            Box(Modifier.size(width = 44.dp, height = 26.dp), Alignment.Center) {
                Text("${(scale * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = Muted)
            }
        }
    }
}

@Composable
private fun ZoomBtn(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = White, modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.titleLarge, color = Ink) }
    }
}

@Composable
private fun Legend(modifier: Modifier = Modifier) {
    val fp = LocalFloorPinColors.current
    Surface(shape = RoundedCornerShape(6.dp), color = White, border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor), shadowElevation = 1.dp, modifier = modifier) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot("Location", fp.accent)
            LegendDot("Open", fp.statusOpen)
            LegendDot("In progress", fp.statusProgress)
            LegendDot("Resolved", fp.statusResolved)
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ink)
    }
}
