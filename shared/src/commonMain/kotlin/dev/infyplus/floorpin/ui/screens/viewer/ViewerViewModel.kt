package dev.infyplus.floorpin.ui.screens.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.infyplus.floorpin.AppContainer
import dev.infyplus.floorpin.data.remote.ApiHttpException
import io.ktor.http.HttpStatusCode
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
    var refreshing by mutableStateOf(false); private set
    var uploading by mutableStateOf(false); private set

    init {
        viewModelScope.launch { floorPlan = container.data.floorPlans.byId(floorPlanId) }
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        refreshing = true; error = null
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
        }.onFailure { e ->
            // The plan was deleted elsewhere while we were looking at it. Drop the stale local copy
            // so backing out lands on a list that matches the server, instead of an entry that
            // 404s every time it is opened.
            if (e is ApiHttpException && e.status == HttpStatusCode.NotFound && container.data.canPrune()) {
                container.data.floorPlans.remove(floorPlanId)
                container.data.sweepOrphans()
                error = "This floor plan no longer exists on the server."
            } else {
                error = e.message
            }
        }
        refreshing = false
    }

    fun observePhotos(issueId: String): Flow<List<Photo>> = container.data.issues.observePhotos(issueId)

    /** Per-location activity history (online-only). Returns null on failure/offline. */
    suspend fun locationActivity(locationId: String): List<dev.infyplus.floorpin.data.remote.ActivityLogDto>? =
        runCatching { container.api.entityActivity("location", locationId) }.getOrNull()

    // ── location writes ──
    fun addLocation(x: Double, y: Double): Location =
        container.data.locations.create(floorPlanId, "", x, y).also { container.sync.requestSync() }
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

    /** Create the issue and record its photos; both are offline-safe and sync in dependency order. */
    fun addIssueWithPhoto(
        locationId: String, title: String, desc: String?, status: IssueStatus, priority: IssuePriority,
        type: String?, category: String?, item: String?, x: Double?, y: Double?, photos: List<Pair<ByteArray, String>>,
    ) {
        val issue = addIssue(locationId, title, desc, status, priority, type, category, item, x, y)
        photos.forEach { (bytes, name) -> uploadPhoto(issue.id, bytes, name) }
    }
    fun setIssueStatus(id: String, status: IssueStatus) {
        container.data.issues.updateStatus(id, status); container.sync.requestSync()
    }
    fun updateIssue(id: String, title: String, desc: String?, priority: IssuePriority, type: String?, category: String?, item: String?) {
        container.data.issues.update(id, title, desc, priority, type, category, item); container.sync.requestSync()
    }
    fun deletePhoto(photoId: String, pending: Boolean) = viewModelScope.launch {
        // A pending photo has never reached the server, so deleting it is purely local and works
        // offline. Calling the REST endpoint for it would 404.
        if (pending) {
            container.data.issues.deletePhoto(photoId)
            return@launch
        }
        uploading = true; error = null
        runCatching { container.api.deletePhoto(photoId) }
            .onSuccess { container.data.issues.deletePhoto(photoId) }
            .onFailure { error = it.message }
        uploading = false
    }
    fun moveIssue(id: String, x: Double, y: Double) {
        container.data.issues.move(id, x, y); container.sync.requestSync()
    }
    fun deleteIssue(id: String) {
        container.data.issues.delete(id); container.sync.requestSync()
    }
    /**
     * Records the photo locally and lets [SyncEngine] upload it.
     *
     * This used to upload first and only write the local row from the server's response, so any
     * failure — offline, an unsynced parent issue, a dropped connection — left the bytes to be
     * garbage collected and the photo was gone. Now the row and its bytes are committed before
     * anything touches the network, and the queue handles ordering and retries.
     */
    fun uploadPhoto(issueId: String, bytes: ByteArray, fileName: String) {
        // The local write can still fail (no disk left, above all). It runs straight off a UI
        // callback, so an escaping exception takes the app down mid-batch and the inspector
        // never learns which photo went missing.
        runCatching { container.data.issues.createLocalPhoto(issueId, bytes, fileName) }
            .onSuccess { container.sync.requestSync() }
            .onFailure { error = "Couldn't save that photo — check your device storage." }
    }

    /** Bytes of a photo that hasn't uploaded yet, so the UI can render it from the local queue. */
    fun photoBytes(photoId: String): ByteArray? = container.data.issues.photoBytes(photoId)
}
