package dev.infyplus.floorpin.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.infyplus.floorpin.ui.theme.Accent
import dev.infyplus.floorpin.ui.theme.Ink
import dev.infyplus.floorpin.ui.theme.White
import kotlinx.coroutines.launch

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
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = false, // hamburger-only; edge-swipe conflicts with canvas pan
                drawerContent = {
                    ModalDrawerSheet(drawerContainerColor = Ink) {
                        Sidebar(
                            items.map { it.copy(onClick = { it.onClick(); scope.launch { drawerState.close() } }) },
                            userName, userRole,
                            { onSignOut(); scope.launch { drawerState.close() } },
                            Modifier.width(268.dp).fillMaxHeight(),
                        )
                    }
                },
            ) {
                content { scope.launch { drawerState.open() } }
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
            Box(Modifier.size(34.dp).background(Accent, RoundedCornerShape(6.dp)), Alignment.Center) {
                Icon(AppIcons.Pin, null, Modifier.size(18.dp), tint = White)
            }
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
