package dev.infyplus.floorpin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.infyplus.floorpin.AppContainer
import dev.infyplus.floorpin.data.remote.AllowlistEntry
import dev.infyplus.floorpin.data.remote.UserDto
import dev.infyplus.floorpin.ui.components.AppButton
import dev.infyplus.floorpin.ui.components.AppCard
import dev.infyplus.floorpin.ui.components.AppTextField
import dev.infyplus.floorpin.ui.components.Avatar
import dev.infyplus.floorpin.ui.components.ButtonVariant
import dev.infyplus.floorpin.ui.components.AppTopBar
import dev.infyplus.floorpin.ui.components.initialsOf
import dev.infyplus.floorpin.ui.theme.Accent
import dev.infyplus.floorpin.ui.theme.BorderColor
import dev.infyplus.floorpin.ui.theme.Ink
import dev.infyplus.floorpin.ui.theme.Muted
import dev.infyplus.floorpin.ui.theme.SurfaceWarm
import dev.infyplus.floorpin.ui.theme.White
import kotlinx.coroutines.launch

class StaffViewModel(private val container: AppContainer) : ViewModel() {
    var users by mutableStateOf<List<UserDto>>(emptyList()); private set
    var allowlist by mutableStateOf<List<AllowlistEntry>>(emptyList()); private set
    var error by mutableStateOf<String?>(null); private set

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        runCatching { container.api.users() }.onSuccess { users = it }.onFailure { error = it.message }
        runCatching { container.api.allowlist() }.onSuccess { allowlist = it }
    }

    fun setRole(userId: String, role: String) = viewModelScope.launch {
        runCatching { container.api.setRole(userId, role) }.onSuccess { refresh() }.onFailure { error = it.message }
    }
    fun toggleBan(user: UserDto) = viewModelScope.launch {
        runCatching { if (user.banned == true) container.api.unban(user.id) else container.api.ban(user.id) }
            .onSuccess { refresh() }.onFailure { error = it.message }
    }
    fun invite(email: String, role: String) = viewModelScope.launch {
        runCatching { container.api.addAllowlist(email, role) }.onSuccess { refresh() }.onFailure { error = it.message }
    }
    fun uninvite(email: String) = viewModelScope.launch {
        runCatching { container.api.removeAllowlist(email) }.onSuccess { refresh() }.onFailure { error = it.message }
    }
}

@Composable
fun StaffScreen(container: AppContainer, currentUser: UserDto, openDrawer: (() -> Unit)?) {
    val vm: StaffViewModel = viewModel { StaffViewModel(container) }
    val adminCount = vm.users.count { it.role.equals("admin", true) }

    Column(Modifier.fillMaxSize().background(SurfaceWarm)) {
        AppTopBar(title = "Staff management", crumb = "Workspace", openDrawer = openDrawer)
        LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Team directory · ${vm.users.size}", style = MaterialTheme.typography.titleLarge, color = Ink)
                        Box(Modifier.padding(top = 8.dp))
                    }
                }
            }
            items(vm.users, key = { it.id }) { user -> UserRow(user, vm, currentUser.id, adminCount) }
            item { InviteCard(vm) }
            if (vm.allowlist.isNotEmpty()) {
                item { Text("Pending / allowlist (${vm.allowlist.size})", style = MaterialTheme.typography.titleMedium, color = Ink) }
                items(vm.allowlist, key = { it.email }) { entry ->
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column { Text(entry.email, color = Ink); Text(entry.role, color = Muted, style = MaterialTheme.typography.labelSmall) }
                            AppButton("Remove", onClick = { vm.uninvite(entry.email) }, small = true, variant = ButtonVariant.Danger)
                        }
                    }
                }
            }
            vm.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
        }
    }
}

@Composable
private fun UserRow(user: UserDto, vm: StaffViewModel, currentUserId: String, adminCount: Int) {
    val isSelf = user.id == currentUserId
    val isAdmin = user.role.equals("admin", true)
    // Can't demote yourself, and can't demote the last remaining admin.
    val canChangeRole = !isSelf && !(isAdmin && adminCount <= 1)
    // Admins (self or peers) can't be banned from here.
    val canBan = !isSelf && !isAdmin
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(initialsOf(user.name ?: user.email ?: "?"))
            Column(Modifier.weight(1f)) {
                Text(if (isSelf) "${user.name ?: "—"} (you)" else user.name ?: "—", color = Ink)
                Text(user.email ?: "", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            RoleChip("Admin", isAdmin) { if (canChangeRole) vm.setRole(user.id, "admin") }
            RoleChip("Staff", user.role.equals("staff", true) || user.role == null) { if (canChangeRole) vm.setRole(user.id, "staff") }
            if (canBan) {
                AppButton(if (user.banned == true) "Unban" else "Ban", onClick = { vm.toggleBan(user) }, small = true, variant = ButtonVariant.Danger)
            }
        }
    }
}

@Composable
private fun RoleChip(label: String, on: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(99.dp), color = if (on) Accent else White, contentColor = if (on) White else Ink, border = androidx.compose.foundation.BorderStroke(1.dp, if (on) Accent else BorderColor)) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun InviteCard(vm: StaffViewModel) {
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("staff") }
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Invite a staff member", style = MaterialTheme.typography.titleLarge, color = Ink)
            AppTextField(email, { email = it }, label = "Email address", placeholder = "name@company.com")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoleChip("Staff", role == "staff") { role = "staff" }
                RoleChip("Admin", role == "admin") { role = "admin" }
            }
            AppButton("Send invite", onClick = { if (email.isNotBlank()) { vm.invite(email.trim().lowercase(), role); email = "" } })
            Text("They'll get a Google Sign-In scoped to assigned projects.", color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}
