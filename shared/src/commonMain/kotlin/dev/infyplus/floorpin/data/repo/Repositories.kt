package dev.infyplus.floorpin.data.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.infyplus.floorpin.data.newId
import dev.infyplus.floorpin.data.nowMillis
import dev.infyplus.floorpin.db.FloorPinDb
import dev.infyplus.floorpin.db.FloorpinQueries
import dev.infyplus.floorpin.db.FloorPlan
import dev.infyplus.floorpin.db.Issue
import dev.infyplus.floorpin.db.Location
import dev.infyplus.floorpin.db.Photo
import dev.infyplus.floorpin.db.Project
import dev.infyplus.floorpin.domain.IssuePriority
import dev.infyplus.floorpin.domain.IssueStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Wraps the outbox queue so writes record a replayable op for POST /api/sync. */
class Outbox(private val q: FloorpinQueries) {
    fun enqueue(entity: String, op: String, entityId: String, data: JsonObject, updatedAt: Long) {
        // Coalesce: fold any pending "update" ops for the same entity into a single op (merging
        // fields, newest wins), so rapid edits/drags don't grow the queue unbounded.
        if (op == "update") {
            val pending = q.outboxUpdatesFor(entity, entityId).executeAsList()
            if (pending.isNotEmpty()) {
                val merged = buildJsonObject {
                    pending.forEach { row ->
                        decode(row.payload).forEach { (k, v) -> put(k, v) }
                    }
                    data.forEach { (k, v) -> put(k, v) }
                }
                q.transaction {
                    pending.forEach { q.dequeue(it.opId) }
                    q.enqueue(newId(), entity, op, entityId, encode(merged), updatedAt)
                }
                return
            }
        }
        q.enqueue(newId(), entity, op, entityId, encode(data), updatedAt)
    }

    private fun encode(data: JsonObject) =
        dev.infyplus.floorpin.data.db.AppJson.encodeToString(JsonObject.serializer(), data)
    private fun decode(payload: String) =
        dev.infyplus.floorpin.data.db.AppJson.decodeFromString(JsonObject.serializer(), payload)
}

class ProjectRepo(private val q: FloorpinQueries) {
    fun observeAll(): Flow<List<Project>> = q.projectsAll().asFlow().mapToList(Dispatchers.Default)
    suspend fun byId(id: String): Project? = q.projectById(id).executeAsOneOrNull()

    fun upsertFromServer(items: List<Project>) = q.transaction {
        items.forEach { q.upsertProject(it.id, it.name, it.description, it.createdAt, it.updatedAt) }
    }
    fun remove(id: String) = q.deleteProject(id)
}

class FloorPlanRepo(private val q: FloorpinQueries) {
    fun observeByProject(projectId: String): Flow<List<FloorPlan>> =
        q.floorPlansByProject(projectId).asFlow().mapToList(Dispatchers.Default)
    suspend fun byId(id: String): FloorPlan? = q.floorPlanById(id).executeAsOneOrNull()

    fun upsertFromServer(items: List<FloorPlan>) = q.transaction {
        items.forEach { q.upsertFloorPlan(it.id, it.projectId, it.name, it.sub, it.imageKey, it.imageUrl, it.createdAt, it.updatedAt, it.width, it.height) }
    }
    fun cacheOne(fp: FloorPlan) =
        q.upsertFloorPlan(fp.id, fp.projectId, fp.name, fp.sub, fp.imageKey, fp.imageUrl, fp.createdAt, fp.updatedAt, fp.width, fp.height)
    fun remove(id: String) = q.deleteFloorPlan(id)
}

class LocationRepo(private val q: FloorpinQueries, private val outbox: Outbox) {
    fun observeByFloorPlan(floorPlanId: String): Flow<List<Location>> =
        q.locationsByFloorPlan(floorPlanId).asFlow().mapToList(Dispatchers.Default)

    fun create(floorPlanId: String, name: String, x: Double, y: Double): Location {
        val now = nowMillis()
        val loc = Location(newId(), floorPlanId, name, x, y, now, now)
        q.upsertLocation(loc.id, loc.floorPlanId, loc.name, loc.x, loc.y, loc.updatedAt, loc.createdAt)
        outbox.enqueue("locations", "create", loc.id, buildJsonObject {
            put("floorPlanId", floorPlanId); put("label", name); put("x", x); put("y", y)
        }, now)
        return loc
    }
    fun rename(id: String, name: String) {
        val now = nowMillis()
        q.renameLocation(name, now, id)
        outbox.enqueue("locations", "update", id, buildJsonObject { put("label", name) }, now)
    }
    fun move(id: String, x: Double, y: Double) {
        val now = nowMillis()
        q.moveLocation(x, y, now, id)
        outbox.enqueue("locations", "update", id, buildJsonObject { put("x", x); put("y", y) }, now)
    }
    fun delete(id: String) {
        q.deleteLocation(id)
        outbox.enqueue("locations", "delete", id, JsonObject(emptyMap()), nowMillis())
    }
    fun upsertFromServer(items: List<Location>) = q.transaction {
        items.forEach { q.upsertLocation(it.id, it.floorPlanId, it.name, it.x, it.y, it.updatedAt, it.createdAt) }
    }
}

class IssueRepo(private val q: FloorpinQueries, private val outbox: Outbox) {
    fun observeByLocation(locationId: String): Flow<List<Issue>> =
        q.issuesByLocation(locationId).asFlow().mapToList(Dispatchers.Default)
    fun observeByFloorPlan(floorPlanId: String): Flow<List<Issue>> =
        q.issuesByFloorPlan(floorPlanId).asFlow().mapToList(Dispatchers.Default)
    fun observePhotos(issueId: String): Flow<List<Photo>> =
        q.photosByIssue(issueId).asFlow().mapToList(Dispatchers.Default)
    fun observePhotosByFloorPlan(floorPlanId: String): Flow<List<Photo>> =
        q.photosByFloorPlan(floorPlanId).asFlow().mapToList(Dispatchers.Default)

    fun create(locationId: String, title: String, description: String?, status: IssueStatus, priority: IssuePriority, type: String? = null, category: String? = null, item: String? = null, x: Double? = null, y: Double? = null): Issue {
        val now = nowMillis()
        val issue = Issue(newId(), locationId, title, description, status.wire, priority.wire, type, x, y, now, now, null, category, item, null)
        q.upsertIssue(issue.id, issue.locationId, issue.title, issue.description, issue.status, issue.priority, issue.type, issue.x, issue.y, issue.createdAt, issue.updatedAt, issue.resolvedAt, issue.category, issue.item, issue.assignedTo)
        outbox.enqueue("issues", "create", issue.id, buildJsonObject {
            put("locationId", locationId); put("title", title)
            put("description", description ?: ""); put("status", status.wire); put("priority", priority.wire)
            if (type != null) put("type", type)
            if (category != null) put("category", category)
            if (item != null) put("item", item)
            if (x != null) put("x", x)
            if (y != null) put("y", y)
        }, now)
        return issue
    }
    fun move(id: String, x: Double, y: Double) {
        val now = nowMillis()
        q.moveIssue(x, y, now, id)
        outbox.enqueue("issues", "update", id, buildJsonObject { put("x", x); put("y", y) }, now)
    }
    fun update(id: String, title: String, description: String?, priority: IssuePriority, type: String?, category: String?, item: String?) {
        val now = nowMillis()
        q.updateIssueDetails(title, description, priority.wire, type, category, item, now, id)
        outbox.enqueue("issues", "update", id, buildJsonObject {
            put("title", title); put("description", description ?: ""); put("priority", priority.wire)
            if (type != null) put("type", type)
            if (category != null) put("category", category)
            if (item != null) put("item", item)
        }, now)
    }
    fun deletePhoto(id: String) = q.deletePhoto(id)
    fun updateStatus(id: String, status: IssueStatus) {
        val now = nowMillis()
        val resolvedAt = if (status == IssueStatus.RESOLVED) now else null
        q.updateIssueStatus(status.wire, resolvedAt, now, id)
        outbox.enqueue("issues", "update", id, buildJsonObject { put("status", status.wire) }, now)
    }
    fun delete(id: String) {
        q.deleteIssue(id)
        outbox.enqueue("issues", "delete", id, JsonObject(emptyMap()), nowMillis())
    }
    fun upsertFromServer(issues: List<Issue>) = q.transaction {
        issues.forEach { q.upsertIssue(it.id, it.locationId, it.title, it.description, it.status, it.priority, it.type, it.x, it.y, it.createdAt, it.updatedAt, it.resolvedAt, it.category, it.item, it.assignedTo) }
    }
    fun upsertPhotos(photos: List<Photo>) = q.transaction {
        photos.forEach { q.upsertPhoto(it.id, it.issueId, it.imageKey, it.imageUrl, it.createdAt, it.caption) }
    }
}

/** Holds the db + queries + repos; built once per session. */
class DataStore(val db: FloorPinDb) {
    val q: FloorpinQueries = db.floorpinQueries
    val outbox = Outbox(q)
    val projects = ProjectRepo(q)
    val floorPlans = FloorPlanRepo(q)
    val locations = LocationRepo(q, outbox)
    val issues = IssueRepo(q, outbox)

    fun pendingOpCount(): Long = q.outboxCount().executeAsOne()
    fun clear() = q.clearAll()
}
