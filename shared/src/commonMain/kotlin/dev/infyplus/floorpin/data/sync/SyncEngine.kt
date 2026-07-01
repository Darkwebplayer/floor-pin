package dev.infyplus.floorpin.data.sync

import dev.infyplus.floorpin.data.db.AppJson
import dev.infyplus.floorpin.data.remote.ApiService
import dev.infyplus.floorpin.data.remote.SyncOp
import dev.infyplus.floorpin.data.repo.DataStore
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
                .onSuccess {
                    // Server processed the batch (applied / stale / missing all terminal) — drop them.
                    data.q.transaction { queued.forEach { data.q.dequeue(it.opId) } }
                    _pending.value = 0
                    _state.value = SyncState.Idle
                }
                .onFailure {
                    queued.forEach { data.q.bumpAttempts(it.opId) }
                    _state.value = SyncState.Offline
                }
        }
    }
}
