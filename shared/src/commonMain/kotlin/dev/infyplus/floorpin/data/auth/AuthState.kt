package dev.infyplus.floorpin.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the server is currently rejecting our session token.
 *
 * Set from a single HTTP interceptor so *any* 401 trips it, not just the ones a screen happens to
 * surface. Deliberately a prompt rather than a silent refresh: Credential Manager's sign-in flow
 * renders an account chooser and needs a live Activity, so a background coroutine cannot quietly
 * mint a new token. See [SessionManager.reauthenticate].
 *
 * Before this existed an expired session was a dead end — the outbox correctly refused to discard
 * the queued work, but nothing told the user why nothing was syncing, and the only visible escape
 * (sign out) wipes the local database and takes that work with it.
 */
class AuthState {
    private val _expired = MutableStateFlow(false)
    val expired: StateFlow<Boolean> = _expired

    fun markExpired() { _expired.value = true }
    fun markValid() { _expired.value = false }
}
