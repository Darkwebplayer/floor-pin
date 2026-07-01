package dev.infyplus.floorpin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.infyplus.floorpin.AppContainer
import dev.infyplus.floorpin.data.remote.toRow
import dev.infyplus.floorpin.db.Project
import dev.infyplus.floorpin.ui.components.AppButton
import dev.infyplus.floorpin.ui.components.AppCard
import dev.infyplus.floorpin.ui.components.AppIcons
import dev.infyplus.floorpin.ui.components.AppTextField
import dev.infyplus.floorpin.ui.components.AppTopBar
import dev.infyplus.floorpin.ui.theme.Muted
import dev.infyplus.floorpin.ui.theme.SurfaceWarm
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectsViewModel(private val container: AppContainer) : ViewModel() {
    val projects = container.data.projects.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    var error by mutableStateOf<String?>(null); private set

    fun refresh() = viewModelScope.launch {
        runCatching { container.api.projects() }
            .onSuccess { dtos -> container.data.projects.upsertFromServer(dtos.map { it.toRow() }) }
            .onFailure { error = it.message }
    }

    fun create(name: String, description: String?, onCreated: (Project) -> Unit) = viewModelScope.launch {
        runCatching { container.api.createProject(name, description) }
            .onSuccess { dto ->
                val row = dto.toRow()
                container.data.projects.upsertFromServer(listOf(row))
                onCreated(row)
            }
            .onFailure { error = it.message }
    }
}

@Composable
fun ProjectsScreen(
    container: AppContainer,
    openDrawer: (() -> Unit)?,
    onOpenProject: (Project) -> Unit,
) {
    val vm: ProjectsViewModel = viewModel { ProjectsViewModel(container) }
    val projects by vm.projects.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refresh() } // refetch each time the screen is entered

    Column(Modifier.fillMaxSize().background(SurfaceWarm)) {
        AppTopBar(title = "Projects", crumb = "Workspace", openDrawer = openDrawer) {
            AppButton("New project", onClick = { showAdd = true }, leadingIcon = AppIcons.Add)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(280.dp),
            modifier = Modifier.fillMaxSize().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(projects, key = { it.id }) { p ->
                AppCard(elevated = true, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)) {
                    Column(Modifier.padding(20.dp).fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleLarge)
                        Text(p.description ?: "—", style = MaterialTheme.typography.bodyMedium, color = Muted)
                        AppButton("Open", onClick = { onOpenProject(p) }, small = true)
                    }
                }
            }
        }
    }

    if (showAdd) {
        CreateProjectDialog(
            onDismiss = { showAdd = false },
            onCreate = { name, desc -> showAdd = false; vm.create(name, desc) { onOpenProject(it) } },
        )
    }
}

@Composable
private fun CreateProjectDialog(onDismiss: () -> Unit, onCreate: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(name, { name = it }, label = "Project name", placeholder = "e.g. Northbridge FM")
                AppTextField(desc, { desc = it }, label = "Description", placeholder = "Optional")
            }
        },
        confirmButton = {
            AppButton("Create", onClick = { if (name.isNotBlank()) onCreate(name.trim(), desc.trim().ifBlank { null }) })
        },
        dismissButton = { AppButton("Cancel", onClick = onDismiss, variant = dev.infyplus.floorpin.ui.components.ButtonVariant.Neutral) },
    )
}
