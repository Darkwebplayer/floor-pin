package dev.infyplus.floorpin.ui.screens.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.infyplus.floorpin.AppContainer
import dev.infyplus.floorpin.data.remote.toRow
import dev.infyplus.floorpin.db.FloorPlan
import dev.infyplus.floorpin.db.Issue
import dev.infyplus.floorpin.db.Location
import dev.infyplus.floorpin.db.Photo
import dev.infyplus.floorpin.domain.IssuePriority
import dev.infyplus.floorpin.domain.IssueStatus
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the floor-plan viewer. All writes go through offline-capable repos. */
class ViewerViewModel(
    private val container: AppContainer,
    private val floorPlanId: String,
) : ViewModel() {

    val locations = container.data.locations.observeByFloorPlan(floorPlanId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val issues = container.data.issues.observeByFloorPlan(floorPlanId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var floorPlan by mutableStateOf<FloorPlan?>(null); private set
    var error by mutableStateOf<String?>(null); private set

    init {
        viewModelScope.launch { floorPlan = container.data.floorPlans.byId(floorPlanId) }
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        runCatching {
            val dto = container.api.floorPlan(floorPlanId)
            container.data.floorPlans.cacheOne(dto.toRow())
            floorPlan = container.data.floorPlans.byId(floorPlanId)
            val locs = dto.locations ?: emptyList()
            container.data.locations.upsertFromServer(locs.map { it.toRow(floorPlanId) })
            locs.forEach { loc ->
                loc.issues?.let { list ->
                    container.data.issues.upsertFromServer(list.map { it.toRow() })
                    list.forEach { i -> i.photos?.let { ph -> container.data.issues.upsertPhotos(ph.map { it.toRow() }) } }
                }
            }
        }.onFailure { error = it.message }
    }

    fun observePhotos(issueId: String): Flow<List<Photo>> = container.data.issues.observePhotos(issueId)

    /** Per-location activity history (online-only). Returns null on failure/offline. */
    suspend fun locationActivity(locationId: String): List<dev.infyplus.floorpin.data.remote.ActivityLogDto>? =
        runCatching { container.api.entityActivity("location", locationId) }.getOrNull()

    // ── location writes ──
    fun addLocation(x: Double, y: Double): Location =
        container.data.locations.create(floorPlanId, "New location", x, y).also { container.sync.requestSync() }
    fun renameLocation(id: String, name: String) {
        container.data.locations.rename(id, name); container.sync.requestSync()
    }
    fun moveLocation(id: String, x: Double, y: Double) {
        container.data.locations.move(id, x, y); container.sync.requestSync()
    }
    fun deleteLocation(id: String) {
        container.data.locations.delete(id); container.sync.requestSync()
    }

    // ── issue writes ──
    fun addIssue(locationId: String, title: String, desc: String?, status: IssueStatus, priority: IssuePriority, type: String? = null, category: String? = null, item: String? = null, x: Double? = null, y: Double? = null): Issue =
        container.data.issues.create(locationId, title, desc, status, priority, type, category, item, x, y).also { container.sync.requestSync() }

    /** Create the issue (offline-ok) then upload an optional photo (online-only, separate endpoint). */
    fun addIssueWithPhoto(
        locationId: String, title: String, desc: String?, status: IssueStatus, priority: IssuePriority,
        type: String?, category: String?, item: String?, x: Double?, y: Double?, photoBytes: ByteArray?, photoName: String?,
    ) {
        val issue = addIssue(locationId, title, desc, status, priority, type, category, item, x, y)
        if (photoBytes != null && photoName != null) uploadPhoto(issue.id, photoBytes, photoName)
    }
    fun setIssueStatus(id: String, status: IssueStatus) {
        container.data.issues.updateStatus(id, status); container.sync.requestSync()
    }
    fun moveIssue(id: String, x: Double, y: Double) {
        container.data.issues.move(id, x, y); container.sync.requestSync()
    }
    fun deleteIssue(id: String) {
        container.data.issues.delete(id); container.sync.requestSync()
    }
    fun uploadPhoto(issueId: String, bytes: ByteArray, fileName: String) = viewModelScope.launch {
        runCatching { container.api.uploadPhoto(issueId, bytes, fileName) }
            .onSuccess { container.data.issues.upsertPhotos(listOf(it.toRow())) }
            .onFailure { error = it.message }
    }
}
