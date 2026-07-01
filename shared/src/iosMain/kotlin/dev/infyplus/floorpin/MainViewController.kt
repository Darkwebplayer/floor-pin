package dev.infyplus.floorpin

import androidx.compose.ui.window.ComposeUIViewController
import dev.infyplus.floorpin.data.auth.GoogleAuthProvider
import dev.infyplus.floorpin.data.auth.TokenStore
import dev.infyplus.floorpin.data.db.DriverFactory

private val container by lazy {
    buildAppContainer(TokenStore(), DriverFactory(), GoogleAuthProvider())
}

fun MainViewController() = ComposeUIViewController { App(container) }
