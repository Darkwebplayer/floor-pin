package dev.infyplus.floorpin.data.sync

import dev.infyplus.floorpin.data.db.AppJson
import dev.infyplus.floorpin.data.nowMillis
import dev.infyplus.floorpin.data.remote.ApiHttpException
import dev.infyplus.floorpin.data.remote.ApiService
import dev.infyplus.floorpin.data.remote.SyncOp
import dev.infyplus.floorpin.data.remote.SyncResponse
import dev.infyplus.floorpin.data.repo.DataStore
import dev.infyplus.floorpin.db.Outbox
import dev.infyplus.floorpin.db.OutboxFailed
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

enum class SyncState { Idle, Syncing, Offline }

// How many times the server may reject an op before we stop retrying it, so one poison op can't
// block the queue forever. Only explicit per-op rejections count — being offline, or having the
// token expire, never does.
internal const val MAX_ATTEMPTS = 5

/** What to do with a queued op once the server has (or hasn't) ruled on it. */
internal enum class OpOutcome { Dequeue, Bump, DeadLetter, Retry }

/**
 * The dead-letter policy, kept pure so it can be tested without a database — this is the decision
 * that used to throw away the user's work.
 *
 * A [verdict] of null means the server returned no result for this op, which happens when it aborts
 * a batch mid-way. That is "unknown", not "done": the op stays queued and the attempt is not
 * counted, because penalising an op the server never even reached is how whole offline sessions
 * were being destroyed. Replaying is safe — creates are idempotent server-side.
 */
internal fun opOutcome(verdict: String?, attempts: Long): OpOutcome = when (verdict) {
    "applied", "stale", "missing" -> OpOutcome.Dequeue
    "rejected" -> if (attempts + 1 >= MAX_ATTEMPTS) OpOutcome.DeadLetter else OpOutcome.Bump
    else -> OpOutcome.Retry
}

/** Send order: a child op must never precede the parent it references. */
internal fun entityRank(entity: String): Int = when (entity) {
    "projects" -> 0
    "locations" -> 1
    "issues" -> 2
    else -> 3
}

/**
 * Pushes queued offline writes to POST /api/sync. The pull/reconcile direction
 * is handled by each screen's refresh() (which upserts fresh server rows). A
 * background loop retries while anything is pending; writers call requestSync().
 */
class SyncEngine(
    private val data: DataStore,
    private val api: ApiService,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SyncState.Idle)
    val state: StateFlow<SyncState> = _state
    private val _pending = MutableStateFlow(0L)
    val pending: StateFlow<Long> = _pending

    /** Ops the server rejected [MAX_ATTEMPTS] times. They are no longer retried, but they are kept
     *  and counted here — silently deleting them is what made lost work invisible. */
    private val _failed = MutableStateFlow(0L)
    val failed: StateFlow<Long> = _failed

    /** Dead-lettered ops, for a UI that lets the user retry or discard them. `OutboxFailed` rather
     *  than `Outbox` because the `failedAt IS NOT NULL` filter narrows that column to non-null. */
    fun failedOps(): List<OutboxFailed> = data.q.outboxFailed().executeAsList()

    fun retryFailed() {
        data.q.transaction { failedOps().forEach { data.q.retryFailed(it.opId) } }
        requestSync()
    }

    fun discardFailed() {
        data.q.discardFailed()
        _failed.value = data.q.outboxFailedCount().executeAsOne()
    }

    init {
        scope.launch {
            while (isActive) {
                flush()
                delay(15_000)
            }
        }
    }

    fun requestSync() { scope.launch { flush() } }

    /** Drain the outbox and wait for it. Callers that are about to hit an online-only endpoint for
     *  a row created offline need the row on the server first; [requestSync] doesn't wait. */
    suspend fun syncNow() = flush()

    private suspend fun flush() {
        mutex.withLock {
            val queued = data.q.outboxPending().executeAsList()
            _pending.value = queued.size.toLong() + data.q.pendingPhotoCount().executeAsOne()
            _failed.value = data.q.outboxFailedCount().executeAsOne()
            if (queued.isEmpty()) {
                // Nothing in the JSON queue, but photos may still be waiting on issues that
                // synced in an earlier pass.
                _state.value = if (flushPhotos()) SyncState.Idle else SyncState.Offline
                return@withLock
            }

            _state.value = SyncState.Syncing
            // Parents before children. `updatedAt` is non-unique wall-clock time and coalescing
            // re-stamps it, so timestamp order alone can put an issue create ahead of the location
            // create it depends on — which the server answers with a foreign key error.
            val ordered = queued.sortedWith(compareBy({ entityRank(it.entity) }, { it.updatedAt }))
            val ops = ordered.map {
                SyncOp(it.entity, it.op, it.entityId, AppJson.decodeFromString(JsonObject.serializer(), it.payload), it.updatedAt)
            }
            runCatching { api.sync(ops) }
                .onSuccess { (status, body) ->
                    when {
                        // Auth failure is not the op's fault. Bumping attempts here used to destroy a
                        // whole offline session in ~75s whenever a token expired.
                        status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden ->
                            _state.value = SyncState.Offline

                        status.isSuccess() -> {
                            applyResults(ordered, body)
                            // Photos only after the batch lands: the server rejects a photo whose
                            // parent issue it has never seen, and bytes can't ride in the JSON body.
                            val photosDone = flushPhotos()
                            _pending.value = data.q.outboxCount().executeAsOne() +
                                data.q.pendingPhotoCount().executeAsOne()
                            _failed.value = data.q.outboxFailedCount().executeAsOne()
                            _state.value =
                                if (_pending.value == 0L && photosDone) SyncState.Idle else SyncState.Offline
                        }

                        // Non-2xx, non-auth: the server was reached but couldn't process the batch
                        // (today, a mid-batch throw returns 404/500 with no results). Every op is
                        // still unaccounted for, so retry them all rather than penalising any.
                        else -> _state.value = SyncState.Offline
                    }
                }
                .onFailure {
                    // Network unreachable — retry indefinitely without counting attempts (offline-first).
                    _state.value = SyncState.Offline
                }
        }
    }

    /**
     * Upload photos whose bytes are still local. Runs only after the outbox has been pushed, because
     * `issue_photos.issue_id` is a foreign key — a photo for an issue the server hasn't seen is a 409.
     *
     * Returns true when nothing is left waiting. A photo whose parent issue is still queued is
     * skipped rather than attempted, so it costs no attempt and no error; it goes out on the pass
     * after its issue lands.
     */
    private suspend fun flushPhotos(): Boolean {
        val queue = data.q.pendingPhotos().executeAsList()
        if (queue.isEmpty()) return true
        val blockedIssues = data.q.outboxPending().executeAsList()
            .filter { it.entity == "issues" }.map { it.entityId }.toSet()

        var allDone = true
        for (photo in queue) {
            if (photo.issueId in blockedIssues) { allDone = false; continue }
            val bytes = data.q.photoBlobById(photo.id).executeAsOneOrNull()
            if (bytes == null) {
                // Row says pending but the bytes are gone — nothing to send, and retrying forever
                // would keep the queue permanently non-empty.
                data.q.markPhotoFailed(nowMillis(), photo.id)
                continue
            }
            val result = runCatching {
                api.uploadPhoto(photo.issueId, bytes, photo.fileName ?: "photo.webp", photo.id, photo.caption)
            }
            result.onSuccess { dto -> data.issues.markPhotoUploaded(photo.id, dto.imageKey) }
            result.onFailure { e ->
                allDone = false
                if (e is ApiHttpException) {
                    // The server refused this photo. Only a refusal counts toward giving up, and the
                    // row is kept either way so the user can see it never made it.
                    data.q.bumpPhotoAttempts(photo.id)
                    if (photo.attempts + 1 >= MAX_ATTEMPTS) data.q.markPhotoFailed(nowMillis(), photo.id)
                    // Other photos may still be fine — a 409 is specific to this one's parent issue.
                } else {
                    // Couldn't reach the server. Charge nothing and stop: the rest will fail too, and
                    // burning every photo's attempt budget on one tunnel is how they'd get dropped.
                    return false
                }
            }
        }
        return allDone
    }

    /**
     * Reconcile the batch against the server's per-op verdicts.
     *
     * An op missing from [body] is deliberately left queued: the server aborts the whole batch on an
     * unhandled error, so "no verdict" means "unknown", not "done". Creates are idempotent server-side
     * (`onConflictDoNothing`), so replaying is safe; assuming success is not.
     */
    private fun applyResults(sent: List<Outbox>, body: SyncResponse?) {
        val verdicts = body?.results.orEmpty().associate { it.id to it.status }
        data.q.transaction {
            sent.forEach { row ->
                when (opOutcome(verdicts[row.entityId], row.attempts)) {
                    OpOutcome.Dequeue -> data.q.dequeue(row.opId)
                    OpOutcome.Bump -> data.q.bumpAttempts(row.opId)
                    OpOutcome.DeadLetter ->
                        data.q.markFailed(nowMillis(), "Server rejected ${row.op} ${row.entity}", row.opId)
                    OpOutcome.Retry -> Unit
                }
            }
        }
    }
}
