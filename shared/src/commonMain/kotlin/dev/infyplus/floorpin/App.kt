package dev.infyplus.floorpin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import dev.infyplus.floorpin.data.remote.UserDto
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.infyplus.floorpin.ui.MainShell
import dev.infyplus.floorpin.ui.screens.LoginScreen
import dev.infyplus.floorpin.ui.theme.FloorPinTheme
import dev.infyplus.floorpin.ui.theme.Ink
import dev.infyplus.floorpin.ui.theme.SurfaceWarm
import floorpin.shared.generated.resources.Res
import floorpin.shared.generated.resources.brand_logo
import org.jetbrains.compose.resources.painterResource

@Composable
fun App(container: AppContainer) {
    setSingletonImageLoaderFactory { ctx ->
        ImageLoader.Builder(ctx)
            .components { add(KtorNetworkFetcherFactory(httpClient = container.http)) }
            .build()
    }
    FloorPinTheme {
        var user by remember { mutableStateOf<UserDto?>(null) }
        var checking by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            if (container.session.isSignedIn()) {
                user = runCatching { container.session.currentUser() }.getOrNull()
            }
            checking = false
        }

        when {
            checking -> BrandSplash()
            user == null -> LoginScreen(container.session) { user = it }
            else -> MainShell(container, user!!, onSignedOut = { user = null })
        }
    }
}

/** Branded loading screen: logo + name inline, sharp (vector + text). */
@Composable
private fun BrandSplash() {
    Box(Modifier.fillMaxSize().background(SurfaceWarm), Alignment.Center) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(painterResource(Res.drawable.brand_logo), null, Modifier.size(44.dp))
            Text("FloorPin", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Light)
        }
    }
}
