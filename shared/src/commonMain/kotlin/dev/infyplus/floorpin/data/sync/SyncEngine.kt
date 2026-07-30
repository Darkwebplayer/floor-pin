package dev.infyplus.floorpin.data.sync

import dev.infyplus.floorpin.data.db.AppJson
import dev.infyplus.floorpin.data.remote.ApiService
import dev.infyplus.floorpin.data.remote.SyncOp
import dev.infyplus.floorpin.data.repo.DataStore
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

// How many times the server may reject an op before we drop it, so one poison op can't
// block the queue forever. Only server rejections count — being offline never does.
private const val MAX_ATTEMPTS = 5

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
            _pending.value = queued.size.toLong()
            if (queued.isEmpty()) {
                _state.value = SyncState.Idle
                return@withLock
            }

            _state.value = SyncState.Syncing
            val ops = queued.map {
                SyncOp(it.entity, it.op, it.entityId, AppJson.decodeFromString(JsonObject.serializer(), it.payload), it.updatedAt)
            }
            runCatching { api.sync(ops) }
                .onSuccess { status ->
                    if (status.isSuccess()) {
                        // Server processed the batch (applied / stale / missing all terminal) — drop them.
                        data.q.transaction { queued.forEach { data.q.dequeue(it.opId) } }
                        _pending.value = 0
                        _state.value = SyncState.Idle
                    } else {
                        // Server reached but rejected the batch. Count it; drop ops that have exhausted
                        // their retries so a permanently-rejected op can't block everything behind it.
                        data.q.transaction {
                            queued.forEach {
                                if (it.attempts + 1 >= MAX_ATTEMPTS) data.q.dequeue(it.opId)
                                else data.q.bumpAttempts(it.opId)
                            }
                        }
                        _pending.value = data.q.outboxCount().executeAsOne()
                        _state.value = SyncState.Offline
                    }
                }
                .onFailure {
                    // Network unreachable — retry indefinitely without counting attempts (offline-first).
                    _state.value = SyncState.Offline
                }
        }
    }
}
