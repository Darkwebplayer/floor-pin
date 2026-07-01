package dev.infyplus.floorpin.data.auth

/** Persists the Better Auth session bearer token. Android = EncryptedSharedPreferences. */
expect class TokenStore {
    fun token(): String?
    fun save(token: String)
    fun clear()
}
