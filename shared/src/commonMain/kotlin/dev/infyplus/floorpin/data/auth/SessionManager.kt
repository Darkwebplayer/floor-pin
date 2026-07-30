package dev.infyplus.floorpin.data.auth

import dev.infyplus.floorpin.data.remote.ApiService
import dev.infyplus.floorpin.data.remote.UserDto
import dev.infyplus.floorpin.data.repo.DataStore

class SessionManager(
    private val tokens: TokenStore,
    private val google: GoogleAuthProvider,
    private val api: ApiService,
    private val data: DataStore,
    private val auth: AuthState,
) {
    fun isSignedIn(): Boolean = tokens.token() != null

    /** Native Google sign-in → exchange for session token → load the user. */
    suspend fun signIn(): UserDto {
        val idToken = google.getIdToken()
        val sessionToken = api.signInSocial(idToken)
        tokens.save(sessionToken)
        auth.markValid()
        return api.currentUser() ?: error("Signed in but no session user returned")
    }

    /**
     * Swap an expired session token for a fresh one, keeping the local database intact.
     *
     * Pointedly *not* [signOut] followed by [signIn]: signOut calls `data.clear()`, which truncates
     * the outbox. An expired session is the moment a user is most likely to have unsynced work, so
     * routing recovery through sign-out would delete exactly the work they are trying to save.
     *
     * Shows Google's account chooser, so it must be called from a live Activity — which is why it
     * hangs off a UI action rather than being retried from the background sync loop.
     */
    suspend fun reauthenticate() {
        val idToken = google.getIdToken()
        tokens.save(api.signInSocial(idToken))
        auth.markValid()
    }

    suspend fun currentUser(): UserDto? = api.currentUser()

    /** Unsynced writes that signing out would destroy — `data.clear()` truncates the outbox. */
    fun unsyncedCount(): Long = data.pendingOpCount()

    /**
     * Signs out and wipes the local database.
     *
     * Refuses while writes are still queued unless [force] is set: `data.clear()` truncates the
     * outbox, so signing out on a flaky connection silently threw away the user's whole offline
     * session. Callers should check [unsyncedCount] and confirm before forcing.
     */
    suspend fun signOut(force: Boolean = false) {
        val pending = unsyncedCount()
        if (pending > 0 && !force) error("$pending unsynced change${if (pending == 1L) "" else "s"} would be lost")
        runCatching { api.signOut() }
        tokens.clear()
        data.clear()
        // Otherwise the flag set by the 401 that prompted the sign-out survives into the next
        // session and the shell claims it expired the moment the new user signs in.
        auth.markValid()
    }
}
