package dev.infyplus.floorpin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import dev.infyplus.floorpin.AppContainer
import dev.infyplus.floorpin.Config
import dev.infyplus.floorpin.data.remote.toRow
import dev.infyplus.floorpin.db.FloorPlan
import dev.infyplus.floorpin.ui.components.AppButton
import dev.infyplus.floorpin.ui.components.AppCard
import dev.infyplus.floorpin.ui.components.AppIcons
import dev.infyplus.floorpin.ui.components.AppTextField
import dev.infyplus.floorpin.ui.components.AppTopBar
import dev.infyplus.floorpin.ui.components.ButtonVariant
import dev.infyplus.floorpin.ui.components.CardMenu
import dev.infyplus.floorpin.ui.components.ConfirmDialog
import dev.infyplus.floorpin.ui.components.rememberValidator
import dev.infyplus.floorpin.ui.components.validateFileRequired
import dev.infyplus.floorpin.ui.components.validateRequired
import dev.infyplus.floorpin.ui.rememberImagePicker
import dev.infyplus.floorpin.ui.theme.Muted
import dev.infyplus.floorpin.ui.theme.SurfaceWarm
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

fun floorPlanImageUrl(fp: FloorPlan): String? =
    fp.imageUrl ?: fp.imageKey?.let { "${Config.BASE_URL}/files/$it" }

class FloorPlansViewModel(private val container: AppContainer, private val projectId: String) : ViewModel() {
    val plans = container.data.floorPlans.observeByProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    var error by mutableStateOf<String?>(null); private set
    var uploading by mutableStateOf(false); private set

    fun refresh() = viewModelScope.launch {
        runCatching { container.api.floorPlans(projectId) }
            .onSuccess { dtos -> container.data.floorPlans.upsertFromServer(dtos.map { it.toRow() }) }
            .onFailure { error = it.message }
    }

    fun upload(name: String, bytes: ByteArray, fileName: String, onDone: (FloorPlan) -> Unit) = viewModelScope.launch {
        uploading = true
        runCatching { container.api.uploadFloorPlan(projectId, name, bytes, fileName) }
            .onSuccess { dto ->
                val row = dto.toRow()
                container.data.floorPlans.cacheOne(row)
                onDone(row)
            }
            .onFailure { error = it.message }
        uploading = false
    }

    fun delete(id: String) = viewModelScope.launch {
        runCatching { container.api.deleteFloorPlan(id) }
            .onSuccess { container.data.floorPlans.remove(id) }
            .onFailure { error = it.message }
    }
}

@Composable
fun FloorPlansScreen(
    container: AppContainer,
    projectId: String,
    projectName: String,
    onBack: () -> Unit,
    onOpenPlan: (FloorPlan) -> Unit,
) {
    val vm: FloorPlansViewModel = viewModel(key = projectId) { FloorPlansViewModel(container, projectId) }
    val plans by vm.plans.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<FloorPlan?>(null) }
    LaunchedEffect(projectId) { vm.refresh() } // refetch each time the screen is entered

    Column(Modifier.fillMaxSize().background(SurfaceWarm)) {
        AppTopBar(title = "Floor Plans", crumb = projectName, onBack = onBack) {
            AppButton("Add floor plan", onClick = { showAdd = true }, leadingIcon = AppIcons.Add)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(280.dp),
            modifier = Modifier.fillMaxSize().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            vm.error?.let { error ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            items(plans, key = { it.id }) { fp ->
                AppCard(elevated = true, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(SurfaceWarm)) {
                            val url = floorPlanImageUrl(fp)
                            if (url != null) {
                                AsyncImage(model = url, contentDescription = fp.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                        }
                        Column(Modifier.padding(16.dp).fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(fp.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                CardMenu(onDelete = { deleting = fp })
                            }
                            Text(fp.sub ?: "—", style = MaterialTheme.typography.bodyMedium, color = Muted)
                            AppButton("Open inspection", onClick = { onOpenPlan(fp) }, small = true)
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddFloorPlanDialog(
            uploading = vm.uploading,
            onDismiss = { showAdd = false },
            onCreate = { name, bytes, fileName -> vm.upload(name, bytes, fileName) { showAdd = false; onOpenPlan(it) } },
        )
    }
    deleting?.let { fp ->
        ConfirmDialog(
            title = "Delete floor plan?",
            message = "\"${fp.name}\" and all its locations, issues and photos will be permanently deleted.",
            onConfirm = { vm.delete(fp.id) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun AddFloorPlanDialog(
    uploading: Boolean,
    onDismiss: () -> Unit,
    onCreate: (name: String, bytes: ByteArray, fileName: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Pair<ByteArray, String>?>(null) }
    val pick = rememberImagePicker { bytes, fileName -> picked = bytes to fileName }
    val validator = rememberValidator()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a floor plan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(
                    name, { name = it; validator.clearField("name") },
                    label = "Plan name", placeholder = "e.g. Level 3",
                    isError = validator.hasError("name"),
                    supportingText = validator.errorFor("name"),
                    required = true,
                )
                AppButton(
                    if (picked != null) "Image selected ✓" else "Choose floor-plan image",
                    onClick = { pick(); validator.clearField("file") },
                    variant = ButtonVariant.Neutral,
                    leadingIcon = AppIcons.Upload,
                )
                validator.errorFor("file")?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            AppButton(
                if (uploading) "Uploading…" else "Create & open",
                onClick = {
                    val p = picked
                    val n = name.trim()
                    validator.validate(
                        "name" to validateRequired(n, "Plan name"),
                        "file" to validateFileRequired(p?.first),
                    )
                    if (validator.valid && !uploading) onCreate(n, p!!.first, p.second)
                },
            )
        },
        dismissButton = { AppButton("Cancel", onClick = onDismiss, variant = ButtonVariant.Neutral) },
    )
}
