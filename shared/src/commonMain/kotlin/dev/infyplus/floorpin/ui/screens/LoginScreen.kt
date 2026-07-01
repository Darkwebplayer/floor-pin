package dev.infyplus.floorpin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.infyplus.floorpin.data.auth.SessionManager
import dev.infyplus.floorpin.data.remote.UserDto
import dev.infyplus.floorpin.ui.components.AppIcons
import dev.infyplus.floorpin.ui.theme.Accent
import dev.infyplus.floorpin.ui.theme.Ink
import dev.infyplus.floorpin.ui.theme.Muted
import dev.infyplus.floorpin.ui.theme.SurfaceWarm
import dev.infyplus.floorpin.ui.theme.White
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(session: SessionManager, onSignedIn: (UserDto) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(White)) {
        if (maxWidth >= 860.dp) {
            Row(Modifier.fillMaxSize()) {
                PromoPanel(Modifier.weight(1.05f).fillMaxHeight())
                Box(Modifier.weight(1f).fillMaxHeight().background(White), Alignment.Center) {
                    SignInCard(session, onSignedIn)
                }
            }
        } else {
            Box(Modifier.fillMaxSize().background(White), Alignment.Center) {
                SignInCard(session, onSignedIn)
            }
        }
    }
}

@Composable
private fun PromoPanel(modifier: Modifier) {
    Column(modifier.background(Ink).padding(56.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(34.dp).background(Accent, RoundedCornerShape(6.dp)), Alignment.Center) {
                Icon(AppIcons.Pin, null, Modifier.size(18.dp), tint = White)
            }
            Text("FloorPin", color = White, fontSize = 22.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Inspections, pinned to the plan.",
            color = White,
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.widthIn(max = 360.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "Map locations, log defects with photo evidence, and export client-ready reports — all on top of your floor plan.",
            color = White.copy(alpha = 0.68f),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Stat("8×", "faster snagging")
            Stat("100%", "visual traceability")
            Stat("PDF", "in one tap")
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(value, color = White, fontSize = 32.sp, fontWeight = FontWeight.Light)
        Text(label, color = White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SignInCard(session: SessionManager, onSignedIn: (UserDto) -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.widthIn(max = 380.dp).padding(32.dp)) {
        Text("WELCOME BACK", color = Accent, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.6.sp)
        Spacer(Modifier.height(12.dp))
        Text("Sign in to FloorPin", style = MaterialTheme.typography.headlineLarge, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "Use your work Google account to access your projects and inspections.",
            color = Muted, style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(32.dp))

        Surface(
            onClick = {
                if (loading) return@Surface
                loading = true; error = null
                scope.launch {
                    runCatching { session.signIn() }
                        .onSuccess { onSignedIn(it) }
                        .onFailure { error = it.message ?: "Sign-in failed"; loading = false }
                }
            },
            shape = RoundedCornerShape(6.dp),
            color = White,
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceWarm),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Continue with Google", color = Ink, fontWeight = FontWeight.Medium)
            }
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "By continuing you agree to the Terms and Privacy Policy. Access is scoped to projects assigned by your administrator.",
            color = Muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Start,
        )
    }
}
