package dev.infyplus.floorpin.data.remote

import dev.infyplus.floorpin.db.FloorPlan
import dev.infyplus.floorpin.db.Issue
import dev.infyplus.floorpin.db.Location
import dev.infyplus.floorpin.db.Photo
import dev.infyplus.floorpin.db.Project
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
fun parseIso(s: String?): Long =
    s?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: 0L

@Serializable
data class ProjectDto(
    val id: String,
    val name: String = "",
    val description: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val issues: List<IssueDto>? = null,
)

@Serializable
data class FloorPlanDto(
    val id: String,
    val projectId: String = "",
    val name: String = "",
    val imageKey: String? = null,
    val width: Double? = null,
    val height: Double? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val locations: List<LocationDto>? = null,
)

@Serializable
data class LocationDto(
    val id: String,
    val floorPlanId: String? = null,
    val label: String? = null,
    val x: Double = 50.0,
    val y: Double = 50.0,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val issues: List<IssueDto>? = null,
)

@Serializable
data class IssueDto(
    val id: String,
    val locationId: String? = null,
    val title: String = "",
    val description: String? = null,
    val status: String = "open",
    val priority: String? = null,
    val type: String? = null,
    val category: String? = null,
    val item: String? = null,
    val assignedTo: String? = null,
    val x: Double? = null,
    val y: Double? = null,
    val resolvedAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val photos: List<PhotoDto>? = null,
)

@Serializable
data class PhotoDto(
    val id: String,
    val issueId: String? = null,
    val imageKey: String? = null,
    val caption: String? = null,
    val createdAt: String? = null,
)

// ── auth ──
@Serializable
data class IdTokenWrapper(val token: String)

@Serializable
data class SocialSignInRequest(val provider: String, val idToken: IdTokenWrapper)

@Serializable
data class UserDto(
    val id: String,
    val name: String? = null,
    val email: String? = null,
    val role: String? = null,
    val banned: Boolean? = null,
)

// ── sync ──
@Serializable
data class SyncOp(
    val entity: String,
    val op: String,
    val id: String,
    val data: JsonObject,
    val updatedAt: Long,
)

@Serializable
data class SyncRequest(val ops: List<SyncOp>)

@Serializable
data class SyncResult(val id: String, val status: String)

@Serializable
data class SyncResponse(
    val serverTime: Long = 0,
    val results: List<SyncResult> = emptyList(),
)

// ── activity ──
@Serializable
data class ActivityLogDto(
    val id: String,
    val userId: String? = null,
    val action: String = "",
    val entityType: String? = null,
    val entityId: String? = null,
    val meta: JsonObject? = null,
    val createdAt: String? = null,
)

// ── allowlist ──
@Serializable
data class AllowlistEntry(val email: String, val role: String = "staff")

// ── mappers DTO → DB rows (timestamps parsed ISO → epoch ms) ──
fun ProjectDto.toRow() = Project(id, name, description, parseIso(createdAt), parseIso(updatedAt))
fun FloorPlanDto.toRow() = FloorPlan(id, projectId, name, null, imageKey, null, parseIso(createdAt), parseIso(updatedAt), width, height)
fun LocationDto.toRow(planId: String) = Location(id, floorPlanId ?: planId, label ?: "Location", x, y, parseIso(updatedAt), parseIso(createdAt))
fun IssueDto.toRow() = Issue(id, locationId ?: "", title, description, status, priority, type, x, y, parseIso(createdAt), parseIso(updatedAt), if (resolvedAt != null) parseIso(resolvedAt) else null, category, item, assignedTo)
// A row from the server is uploaded by definition: pending = 0, no local bytes, no failure.
fun PhotoDto.toRow() = Photo(id, issueId ?: "", imageKey, null, parseIso(createdAt), caption, 0, null, 0, null)
