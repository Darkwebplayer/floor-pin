package dev.infyplus.floorpin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.infyplus.floorpin.AppContainer
import dev.infyplus.floorpin.data.remote.UserDto
import dev.infyplus.floorpin.ui.components.AppIcons
import dev.infyplus.floorpin.ui.components.AppTopBar
import dev.infyplus.floorpin.ui.components.ConfirmDialog
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
    // Signing out truncates the outbox, so unsynced work needs an explicit confirmation.
    var confirmSignOut by remember { mutableStateOf<Long?>(null) }
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
        onSignOut = {
            val unsynced = container.session.unsyncedCount()
            if (unsynced > 0) confirmSignOut = unsynced
            else scope.launch { container.session.signOut(); onSignedOut() }
        },
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

    confirmSignOut?.let { n ->
        ConfirmDialog(
            title = "Sign out with unsynced work?",
            message = "$n change${if (n == 1L) "" else "s"} ${if (n == 1L) "has" else "have"} not reached the server yet. " +
                "Signing out erases the local copy, so ${if (n == 1L) "it" else "they"} will be lost. " +
                "Reconnect and wait for the offline bar to clear to keep ${if (n == 1L) "it" else "them"}.",
            confirmLabel = "Sign out anyway",
            onConfirm = {
                confirmSignOut = null
                scope.launch { container.session.signOut(force = true); onSignedOut() }
            },
            onDismiss = { confirmSignOut = null },
        )
    }
}

@Composable
private fun SyncBar(container: AppContainer) {
    val state by container.sync.state.collectAsStateWithLifecycle()
    val pending by container.sync.pending.collectAsStateWithLifecycle()
    val failed by container.sync.failed.collectAsStateWithLifecycle()
    var showFailed by remember { mutableStateOf(false) }

    // Rejected work outranks pending work: pending resolves itself, rejected never will. It used to
    // be deleted outright, which cleared this bar and read to the user as "everything synced".
    if (failed > 0L) {
        Box(
            Modifier.fillMaxWidth()
                .background(dev.infyplus.floorpin.ui.theme.Danger)
                .clickable { showFailed = true }
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(
                "$failed change${if (failed == 1L) "" else "s"} rejected by the server — tap for options",
                color = dev.infyplus.floorpin.ui.theme.White,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
        }
    } else if (state == dev.infyplus.floorpin.data.sync.SyncState.Offline) {
        // The transient "Syncing" flashed on every edit and was just noise; the offline bar
        // clears itself once reconnected and flushed.
        Box(Modifier.fillMaxWidth().background(dev.infyplus.floorpin.ui.theme.Warn).padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                "Offline — $pending change${if (pending == 1L) "" else "s"} pending",
                color = dev.infyplus.floorpin.ui.theme.White,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (showFailed) {
        val ops = remember { container.sync.failedOps() }
        ConfirmDialog(
            title = "Changes the server rejected",
            message = ops.take(5).joinToString("\n") { "• ${it.op} ${it.entity}: ${it.lastError ?: "no detail"}" } +
                (if (ops.size > 5) "\n…and ${ops.size - 5} more" else "") +
                "\n\nRetry sends them again. Discard removes them from the queue — the local copy stays on this device but will never reach the server.",
            confirmLabel = "Discard",
            onConfirm = { container.sync.discardFailed(); showFailed = false },
            onDismiss = { showFailed = false },
            neutralLabel = "Retry",
            onNeutral = { container.sync.retryFailed(); showFailed = false },
        )
    }
}

