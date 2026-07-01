package dev.infyplus.floorpin.data.auth

/** Returns a Google ID token for the chosen account, to exchange with Better Auth. */
expect class GoogleAuthProvider {
    suspend fun getIdToken(): String
}
