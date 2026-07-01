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

    suspend fun signOut() {
        runCatching { api.signOut() }
        tokens.clear()
        data.clear()
    }
}
