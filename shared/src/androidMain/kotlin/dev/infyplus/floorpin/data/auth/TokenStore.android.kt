package dev.infyplus.floorpin.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

actual class TokenStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "floorpin_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    actual fun token(): String? = prefs.getString(KEY, null)
    actual fun save(token: String) { prefs.edit().putString(KEY, token).apply() }
    actual fun clear() { prefs.edit().remove(KEY).apply() }

    private companion object { const val KEY = "session_token" }
}
