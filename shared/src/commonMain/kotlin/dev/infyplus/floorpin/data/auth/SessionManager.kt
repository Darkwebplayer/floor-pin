package dev.infyplus.floorpin.data.auth

import dev.infyplus.floorpin.data.remote.ApiService
import dev.infyplus.floorpin.data.remote.UserDto
import dev.infyplus.floorpin.data.repo.DataStore

class SessionManager(
    private val tokens: TokenStore,
    private val google: GoogleAuthProvider,
    private val api: ApiService,
    private val data: DataStore,
) {
    fun isSignedIn(): Boolean = tokens.token() != null

    /** Native Google sign-in → exchange for session token → load the user. */
    suspend fun signIn(): UserDto {
        val idToken = google.getIdToken()
        val sessionToken = api.signInSocial(idToken)
        tokens.save(sessionToken)
        return api.currentUser() ?: error("Signed in but no session user returned")
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
    }
}
