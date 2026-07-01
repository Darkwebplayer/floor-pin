package dev.infyplus.floorpin.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * @param activityContext an Activity context (Credential Manager renders UI).
 * @param serverClientId  the Web OAuth client id (Config.GOOGLE_CLIENT_ID).
 */
actual class GoogleAuthProvider(
    private val activityContext: Context,
    private val serverClientId: String,
) {
    private val credentialManager = CredentialManager.create(activityContext)

    actual suspend fun getIdToken(): String {
        // Explicit "Sign in with Google" flow (full account chooser), NOT One Tap.
        // One Tap (GetGoogleIdOption) returns a false TYPE_USER_CANCELED right after
        // account selection when it can't silently complete; this flow doesn't.
        val option = GetSignInWithGoogleOption.Builder(serverClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = try {
            credentialManager.getCredential(activityContext, request)
        } catch (e: GetCredentialException) {
            // Credential Manager reports config failures (unregistered SHA-1, wrong
            // project, consent screen / test-user issues) as a "cancellation". Log the
            // real type+message so the cause is visible instead of "user cancelled".
            Log.e("GoogleAuth", "getCredential failed: ${e.type} — ${e.message}", e)
            throw e
        }
        val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
        return credential.idToken
    }
}
