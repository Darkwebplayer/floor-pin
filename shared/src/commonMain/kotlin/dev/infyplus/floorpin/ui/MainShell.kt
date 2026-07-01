package dev.infyplus.floorpin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.infyplus.floorpin.AppContainer
import dev.infyplus.floorpin.data.remote.UserDto
import dev.infyplus.floorpin.ui.components.AppIcons
import dev.infyplus.floorpin.ui.components.AppTopBar
import dev.infyplus.floorpin.ui.components.NavItem
import dev.infyplus.floorpin.ui.components.NavScaffold
import dev.infyplus.floorpin.ui.nav.Navigator
import dev.infyplus.floorpin.ui.nav.Screen
import dev.infyplus.floorpin.ui.screens.FloorPlansScreen
import dev.infyplus.floorpin.ui.screens.ProjectsScreen
import dev.infyplus.floorpin.ui.theme.SurfaceWarm
import kotlinx.coroutines.launch

@Composable
fun MainShell(container: AppContainer, user: UserDto, onSignedOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    val nav = remember { Navigator(Screen.Projects) }
    val isAdmin = user.role.equals("admin", ignoreCase = true)
    val current = nav.current

    PlatformBackHandler(enabled = nav.canPop) { nav.pop() }

    val inProjectsTree = current is Screen.Projects || current is Screen.FloorPlans ||
        current is Screen.Viewer || current is Screen.Report
    val items = buildList {
        add(NavItem("Projects", AppIcons.Map, selected = inProjectsTree) { nav.replaceRoot(Screen.Projects) })
        if (isAdmin) add(NavItem("Staff", AppIcons.Users, selected = current is Screen.Staff) { nav.replaceRoot(Screen.Staff) })
    }

    NavScaffold(
        items = items,
        userName = user.name ?: user.email ?: "User",
        userRole = user.role ?: "Staff",
        onSignOut = { scope.launch { container.session.signOut(); onSignedOut() } },
    ) { openDrawer ->
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            SyncBar(container)
            Box(Modifier.weight(1f)) {
                when (val s = current) {
                    is Screen.Projects -> ProjectsScreen(container, openDrawer) { p ->
                        nav.push(Screen.FloorPlans(p.id, p.name))
                    }
                    is Screen.FloorPlans -> FloorPlansScreen(container, s.projectId, s.projectName, onBack = { nav.pop() }) { fp ->
                        nav.push(Screen.Viewer(fp.id, fp.name))
                    }
                    is Screen.Viewer -> dev.infyplus.floorpin.ui.screens.viewer.ViewerScreen(
                        container, s.floorPlanId, s.floorPlanName,
                        onBack = { nav.pop() },
                        onOpenReport = { nav.push(Screen.Report(s.floorPlanId)) },
                    )
                    is Screen.Report -> dev.infyplus.floorpin.ui.screens.ReportScreen(container, s.floorPlanId, onBack = { nav.pop() })
                    is Screen.Staff -> dev.infyplus.floorpin.ui.screens.StaffScreen(container, user, openDrawer)
                }
            }
        }
    }
}

@Composable
private fun SyncBar(container: AppContainer) {
    val state by container.sync.state.collectAsStateWithLifecycle()
    val pending by container.sync.pending.collectAsStateWithLifecycle()
    // Only surface a bar when offline. The transient "Syncing" flashed on every edit
    // and was just noise; the offline bar clears itself once reconnected and flushed.
    val text = if (state == dev.infyplus.floorpin.data.sync.SyncState.Offline)
        "Offline — $pending change${if (pending == 1L) "" else "s"} pending" else null
    if (text != null) {
        Box(Modifier.fillMaxWidth().background(dev.infyplus.floorpin.ui.theme.Warn).padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(text, color = dev.infyplus.floorpin.ui.theme.White, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }
    }
}

