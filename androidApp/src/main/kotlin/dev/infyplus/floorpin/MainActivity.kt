package dev.infyplus.floorpin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.infyplus.floorpin.data.auth.GoogleAuthProvider
import dev.infyplus.floorpin.data.auth.TokenStore
import dev.infyplus.floorpin.data.db.DriverFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = buildAppContainer(
            tokens = TokenStore(applicationContext),
            driverFactory = DriverFactory(applicationContext),
            google = GoogleAuthProvider(this, Config.GOOGLE_CLIENT_ID),
        )

        setContent { App(container) }
    }
}
