package dev.infyplus.floorpin.data.auth

actual class GoogleAuthProvider {
    actual suspend fun getIdToken(): String =
        throw NotImplementedError("iOS Google sign-in is out of v1 scope")
}
