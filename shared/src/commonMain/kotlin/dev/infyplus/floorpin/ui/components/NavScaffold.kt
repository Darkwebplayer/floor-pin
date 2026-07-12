package dev.infyplus.floorpin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.infyplus.floorpin.ui.theme.Accent
import dev.infyplus.floorpin.ui.theme.DarkModeOverride
import dev.infyplus.floorpin.ui.theme.Ink
import dev.infyplus.floorpin.ui.theme.White
import floorpin.shared.generated.resources.Res
import floorpin.shared.generated.resources.brand_logo
import org.jetbrains.compose.resources.painterResource

data class NavItem(val label: String, val icon: ImageVector, val selected: Boolean, val onClick: () -> Unit)

/**
 * App frame: permanent dark sidebar ≥860dp, off-canvas modal drawer below that
 * (mirrors shell.js). `content` receives `openDrawer` — non-null only on narrow
 * layouts, so a screen knows whether to show a hamburger.
 */
@Composable
fun NavScaffold(
    items: List<NavItem>,
    userName: String,
    userRole: String,
    onSignOut: () -> Unit,
    content: @Composable (openDrawer: (() -> Unit)?) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 860.dp) {
            Row(Modifier.fillMaxSize()) {
                Sidebar(items, userName, userRole, onSignOut, Modifier.width(248.dp).fillMaxHeight())
                Box(Modifier.weight(1f).fillMaxHeight()) { content(null) }
            }
        } else {
            // Custom off-canvas drawer: hamburger-only (no edge-swipe, which conflicts
            // with canvas pan) but tap-outside on the scrim dismisses it.
            var open by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxSize()) {
                content { open = true }
                AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.32f))
                            .pointerInput(Unit) { detectTapGestures { open = false } },
                    )
                }
                AnimatedVisibility(
                    open,
                    enter = slideInHorizontally { -it },
                    exit = slideOutHorizontally { -it },
                ) {
                    Surface(color = Ink, modifier = Modifier.width(268.dp).fillMaxHeight()) {
                        Sidebar(
                            items.map { it.copy(onClick = { it.onClick(); open = false }) },
                            userName, userRole,
                            { onSignOut(); open = false },
                            Modifier.width(268.dp).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Sidebar(
    items: List<NavItem>,
    userName: String,
    userRole: String,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(Ink).padding(horizontal = 16.dp, vertical = 24.dp)) {
        // brand
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
        ) {
            Image(painterResource(Res.drawable.brand_logo), null, Modifier.size(34.dp))
            Text(buildBrand(), color = White, fontSize = 22.sp, fontWeight = FontWeight.Light)
        }
        Text(
            "INSPECTION",
            color = White.copy(alpha = 0.42f),
            fontSize = 10.sp,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
        )
        items.forEach { NavRow(it) }
        Spacer(Modifier.weight(1f))
        DarkModeRow()
        NavRow(NavItem("Sign out", AppIcons.SignOut, false, onSignOut))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Avatar(initialsOf(userName))
            Column {
                Text(userName, color = White, fontSize = 14.sp)
                Text(userRole, color = White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DarkModeRow() {
    var dark by DarkModeOverride
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp),
    ) {
        Icon(AppIcons.Map, null, Modifier.size(18.dp), tint = White.copy(alpha = 0.78f))
        Spacer(Modifier.width(12.dp))
        Text("Dark mode", color = White.copy(alpha = 0.78f), fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = dark,
            onCheckedChange = { dark = it },
            colors = SwitchDefaults.colors(checkedTrackColor = Accent),
        )
    }
}

@Composable
private fun NavRow(item: NavItem) {
    val bg = if (item.selected) Accent else Color.Transparent
    val fg = if (item.selected) White else White.copy(alpha = 0.78f)
    Surface(
        onClick = item.onClick,
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.height(44.dp).padding(horizontal = 12.dp),
        ) {
            Icon(item.icon, null, Modifier.size(18.dp))
            Text(item.label, fontSize = 14.sp)
        }
    }
}

@Composable
private fun buildBrand() = androidx.compose.ui.text.buildAnnotatedString {
    append("Floor")
    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Normal))
    append("Pin")
    pop()
}

fun initialsOf(name: String): String =
    name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
