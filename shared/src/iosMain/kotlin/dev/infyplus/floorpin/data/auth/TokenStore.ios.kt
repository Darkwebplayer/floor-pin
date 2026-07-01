package dev.infyplus.floorpin.data.auth

// Stub: iOS auth is out of v1 scope. Swap for Keychain later. In-memory only.
actual class TokenStore {
    private var value: String? = null
    actual fun token(): String? = value
    actual fun save(token: String) { value = token }
    actual fun clear() { value = null }
}
