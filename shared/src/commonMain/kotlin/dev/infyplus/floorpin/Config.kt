package dev.infyplus.floorpin

/**
 * App-wide configuration. The Google client id is the **Web** OAuth client
 * (used as serverClientId / ID-token audience) — public, not a secret.
 *
 * BASE_URL points at the local wrangler dev server. `10.0.2.2` is the host
 * machine's loopback as seen from the Android emulator. Swap for prod.
 */
object Config {
    //const val BASE_URL: String = "https://floorpin.darkwebplayer101.workers.dev"
    const val BASE_URL: String = "http://10.0.2.2:8787"
    const val GOOGLE_CLIENT_ID: String =
        "12390791695-eantvccvjqq6ncv8daj0b4evpderkmvi.apps.googleusercontent.com"
}
