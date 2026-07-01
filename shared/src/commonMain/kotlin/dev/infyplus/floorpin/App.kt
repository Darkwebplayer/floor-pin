package dev.infyplus.floorpin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
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
import dev.infyplus.floorpin.ui.MainShell
import dev.infyplus.floorpin.ui.screens.LoginScreen
import dev.infyplus.floorpin.ui.theme.FloorPinTheme

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
            checking -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            user == null -> LoginScreen(container.session) { user = it }
            else -> MainShell(container, user!!, onSignedOut = { user = null })
        }
    }
}
