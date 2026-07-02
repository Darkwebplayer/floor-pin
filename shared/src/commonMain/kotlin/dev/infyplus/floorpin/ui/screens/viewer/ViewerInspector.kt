package dev.infyplus.floorpin.ui.screens.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.infyplus.floorpin.Config
import dev.infyplus.floorpin.data.remote.ActivityLogDto
import dev.infyplus.floorpin.db.Issue
import dev.infyplus.floorpin.db.Location
import dev.infyplus.floorpin.domain.IssuePriority
import dev.infyplus.floorpin.domain.IssueStatus
import dev.infyplus.floorpin.ui.components.AppButton
import dev.infyplus.floorpin.ui.components.AppIcons
import dev.infyplus.floorpin.ui.components.AppTextField
import dev.infyplus.floorpin.ui.components.ButtonVariant
import dev.infyplus.floorpin.ui.components.StatusBadge
import dev.infyplus.floorpin.ui.components.rememberPhotoMarker
import dev.infyplus.floorpin.ui.rememberCameraCapture
import dev.infyplus.floorpin.ui.rememberImagePicker
import dev.infyplus.floorpin.ui.theme.Accent
import dev.infyplus.floorpin.ui.theme.BorderColor
import dev.infyplus.floorpin.ui.theme.Ink
import dev.infyplus.floorpin.ui.theme.Muted
import dev.infyplus.floorpin.ui.theme.SurfaceWarm
import dev.infyplus.floorpin.ui.theme.White
import kotlinx.serialization.json.JsonPrimitive

@Composable
fun Inspector(
    vm: ViewerViewModel,
    location: Location?,
    issues: List<Issue>,
    selectedIssueId: String?,
    onSelectIssue: (String?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = White,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth()) {
            // header
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when {
                        location == null -> "Browse"
                        selectedIssueId != null -> "Issue"
                        else -> "Location"
                    },
                    style = MaterialTheme.typography.labelSmall, color = Accent,
                )
                Surface(onClick = onClose, shape = RoundedCornerShape(4.dp), color = White, modifier = Modifier.size(32.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(AppIcons.Close, "Close", Modifier.size(16.dp), tint = Ink) }
                }
            }

            when {
                location == null -> EmptyHint()
                selectedIssueId != null -> {
                    val issue = issues.firstOrNull { it.id == selectedIssueId }
                    if (issue == null) onSelectIssue(null)
                    else IssueDetail(vm, location, issue, onBack = { onSelectIssue(null) })
                }
                else -> LocationDetail(vm, location, issues, onOpenIssue = { onSelectIssue(it) }, onClose = onClose)
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(AppIcons.Pin, null, Modifier.size(40.dp), tint = BorderColor)
        Text("Tap a pin to view its issues.", color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun LocationDetail(vm: ViewerViewModel, location: Location, issues: List<Issue>, onOpenIssue: (String) -> Unit, onClose: () -> Unit) {
    var name by remember(location.id) { mutableStateOf(location.name) }
    var showAdd by remember { mutableStateOf(false) }
    var tab by remember(location.id) { mutableStateOf(0) }

    // Commit the name once, when this pin's inspector closes (Done, tap-away, or switching pins) —
    // not on every keystroke. Only writes if the name actually changed.
    DisposableEffect(location.id) {
        onDispose {
            val trimmed = name.ifBlank { "Untitled" }
            if (trimmed != location.name) vm.renameLocation(location.id, trimmed)
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(name, style = MaterialTheme.typography.headlineMedium, color = Ink)
        AppTextField(name, { name = it }, label = "Location name", modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            Chip("Issues (${issues.size})", tab == 0) { tab = 0 }
            Chip("Activity", tab == 1) { tab = 1 }
        }
    }

    if (tab == 0) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(260.dp)) {
            if (issues.isEmpty()) item { Text("No issues logged yet.", color = Muted, style = MaterialTheme.typography.bodyMedium) }
            items(issues, key = { it.id }) { issue -> IssueCard(issue) { onOpenIssue(issue.id) } }
        }
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton("Add issue", onClick = { showAdd = true }, small = true, leadingIcon = AppIcons.Add)
            AppButton("Done", onClick = onClose, small = true, variant = ButtonVariant.Neutral)
            AppButton("Remove location", onClick = { vm.deleteLocation(location.id) }, small = true, variant = ButtonVariant.Danger)
        }
    } else {
        ActivityList(vm, location.id)
    }

    if (showAdd) {
        AddIssueDialog(
            onDismiss = { showAdd = false },
            onCreate = { title, desc, status, priority, type, category, item, photoBytes, photoName ->
                showAdd = false
                vm.addIssueWithPhoto(location.id, title, desc, status, priority, type, category, item, location.x, location.y, photoBytes, photoName)
            },
        )
    }
}

@Composable
private fun ActivityList(vm: ViewerViewModel, locationId: String) {
    var loading by remember(locationId) { mutableStateOf(true) }
    var entries by remember(locationId) { mutableStateOf<List<ActivityLogDto>?>(null) }
    LaunchedEffect(locationId) {
        loading = true
        entries = vm.locationActivity(locationId)
        loading = false
    }

    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(300.dp)) {
        when {
            loading -> Text("Loading activity…", color = Muted, style = MaterialTheme.typography.bodyMedium)
            entries.isNullOrEmpty() -> Text(
                if (entries == null) "Couldn't load activity (offline?)." else "No activity yet for this location.",
                color = Muted, style = MaterialTheme.typography.bodyMedium,
            )
            else -> LazyColumn(Modifier.fillMaxWidth()) {
                items(entries!!, key = { it.id }) { ActivityRow(it) }
            }
        }
    }
}

@Composable
private fun ActivityRow(act: ActivityLogDto) {
    val detail = act.meta?.let { m ->
        val from = (m["from"] as? JsonPrimitive)?.content
        val to = (m["to"] as? JsonPrimitive)?.content
        if (from != null && to != null) "$from → $to" else null
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(actionLabel(act.action), style = MaterialTheme.typography.titleMedium, color = Ink)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.padding(top = 2.dp))
        Text(fmtWhen(act.createdAt), style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.padding(top = 4.dp))
    }
}

private fun actionLabel(action: String): String =
    action.split(".").joinToString(" ") { it.replace("_", " ") }.replaceFirstChar { it.uppercase() }

private fun fmtWhen(iso: String?): String =
    iso?.replace("T", " ")?.take(16) ?: ""

@Composable
private fun IssueCard(issue: Issue, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(6.dp), color = White, border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(issue.title, style = MaterialTheme.typography.titleMedium, color = Ink)
            if (!issue.description.isNullOrBlank()) Text(issue.description!!, style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.padding(top = 4.dp))
            Box(Modifier.padding(top = 8.dp)) { StatusBadge(IssueStatus.fromWire(issue.status)) }
        }
    }
}

@Composable
private fun IssueDetail(vm: ViewerViewModel, location: Location, issue: Issue, onBack: () -> Unit) {
    val photos by remember(issue.id) { vm.observePhotos(issue.id) }.collectAsStateWithLifecycle(emptyList())
    val annotate = rememberPhotoMarker()
    val pick = rememberImagePicker { bytes, name ->
        annotate(bytes, name) { result ->
            if (result != null) vm.uploadPhoto(issue.id, result.bytes, result.fileName)
        }
    }
    val capture = rememberCameraCapture { bytes, name ->
        annotate(bytes, name) { result ->
            if (result != null) vm.uploadPhoto(issue.id, result.bytes, result.fileName)
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        AppButton(location.name, onClick = onBack, small = true, variant = ButtonVariant.Neutral, leadingIcon = AppIcons.ChevronLeft)
        Text(issue.title, style = MaterialTheme.typography.headlineMedium, color = Ink, modifier = Modifier.padding(vertical = 12.dp))
        Box(Modifier.padding(bottom = 8.dp)) { StatusBadge(IssueStatus.fromWire(issue.status)) }
        if (!issue.description.isNullOrBlank()) Text(issue.description!!, style = MaterialTheme.typography.bodyMedium, color = Ink)

        // photos
        FlowRow(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            photos.forEach { p ->
                p.imageKey?.let {
                    AsyncImage(
                        model = "${Config.BASE_URL}/files/$it",
                        contentDescription = "Photo",
                        modifier = Modifier.size(72.dp).background(SurfaceWarm, RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton("Take photo", onClick = capture, small = true, variant = ButtonVariant.Neutral, leadingIcon = AppIcons.Camera)
            AppButton("Gallery", onClick = pick, small = true, variant = ButtonVariant.Neutral, leadingIcon = AppIcons.Upload)
        }

        Text("Status", style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
        StatusChips(IssueStatus.fromWire(issue.status)) { vm.setIssueStatus(issue.id, it) }

        AppButton("Remove issue", onClick = { vm.deleteIssue(issue.id); onBack() }, small = true, variant = ButtonVariant.Danger, modifier = Modifier.padding(top = 16.dp, bottom = 20.dp))
    }
}

@Composable
private fun StatusChips(selected: IssueStatus, onSelect: (IssueStatus) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IssueStatus.entries.forEach { s ->
            Chip(s.label, s == selected) { onSelect(s) }
        }
    }
}

@Composable
private fun PriorityChips(selected: IssuePriority, onSelect: (IssuePriority) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IssuePriority.entries.forEach { p ->
            Chip(p.label, p == selected) { onSelect(p) }
        }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(99.dp),
        color = if (on) Accent else White,
        contentColor = if (on) White else Ink,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (on) Accent else BorderColor),
    ) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun AddIssueDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, desc: String?, status: IssueStatus, priority: IssuePriority, type: String?, category: String?, item: String?, photoBytes: ByteArray?, photoName: String?) -> Unit,
) {
    var step by remember { mutableStateOf(0) }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Architectural") }
    var category by remember { mutableStateOf("Architectural") }
    var item by remember { mutableStateOf("General Snag") }
    var status by remember { mutableStateOf(IssueStatus.OPEN) }
    var priority by remember { mutableStateOf(IssuePriority.MEDIUM) }
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var photoName by remember { mutableStateOf<String?>(null) }
    val annotate = rememberPhotoMarker()
    val pickGallery = rememberImagePicker { bytes, name ->
        annotate(bytes, name) { result ->
            if (result != null) { photoBytes = result.bytes; photoName = result.fileName }
        }
    }
    val takePhoto = rememberCameraCapture { bytes, name ->
        annotate(bytes, name) { result ->
            if (result != null) { photoBytes = result.bytes; photoName = result.fileName }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (step == 0) "New issue" else "Add a photo") },
        text = {
            if (step == 0) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(title, { title = it }, label = "Issue title", placeholder = "e.g. Sink not working")
                    AppTextField(desc, { desc = it }, label = "Description", placeholder = "What's wrong…", singleLine = false)
                    AppTextField(type, { type = it }, label = "Type", placeholder = "e.g. Plumbing")
                    AppTextField(category, { category = it }, label = "Category", placeholder = "e.g. Snagging")
                    AppTextField(item, { item = it }, label = "Item", placeholder = "e.g. General Snag")
                    Text("Status", style = MaterialTheme.typography.bodySmall, color = Muted)
                    StatusChips(status) { status = it }
                    Text("Priority", style = MaterialTheme.typography.bodySmall, color = Muted)
                    PriorityChips(priority) { priority = it }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Attach a photo (optional)", style = MaterialTheme.typography.bodySmall, color = Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton("Take photo", onClick = takePhoto, small = true, variant = ButtonVariant.Neutral, leadingIcon = AppIcons.Camera)
                        AppButton("Gallery", onClick = pickGallery, small = true, variant = ButtonVariant.Neutral, leadingIcon = AppIcons.Upload)
                    }
                    photoBytes?.let { b ->
                        AsyncImage(
                            model = b,
                            contentDescription = "Selected photo",
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (step == 0) {
                AppButton("Next", onClick = { if (title.isNotBlank()) step = 1 })
            } else {
                AppButton("Save issue", onClick = {
                    onCreate(title.trim(), desc.trim().ifBlank { null }, status, priority, type.trim().ifBlank { null }, category.trim().ifBlank { null }, item.trim().ifBlank { null }, photoBytes, photoName)
                })
            }
        },
        dismissButton = {
            if (step == 0) AppButton("Cancel", onClick = onDismiss, variant = ButtonVariant.Neutral)
            else AppButton("Back", onClick = { step = 0 }, variant = ButtonVariant.Neutral)
        },
    )
}
