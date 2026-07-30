package dev.infyplus.floorpin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.infyplus.floorpin.ui.theme.Muted

/**
 * Reusable destructive-action confirmation.
 *
 * [neutralLabel]/[onNeutral] add a third, non-destructive choice beside the danger button — for
 * dialogs where "undo the problem" is as valid as "throw it away" (retry vs discard a failed sync).
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Delete",
    loading: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    neutralLabel: String? = null,
    onNeutral: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = if (loading) ({}) else onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (neutralLabel != null && onNeutral != null) {
                    AppButton(neutralLabel, onClick = onNeutral, variant = ButtonVariant.Neutral, enabled = !loading)
                }
                AppButton(confirmLabel, onClick = onConfirm, variant = ButtonVariant.Danger, loading = loading)
            }
        },
        dismissButton = {
            AppButton("Cancel", onClick = onDismiss, variant = ButtonVariant.Neutral, enabled = !loading)
        },
    )
}

/** Small 3-dot overflow menu for a card. Pass a null [onEdit] to show Delete only. */
@Composable
fun CardMenu(onEdit: (() -> Unit)? = null, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(onClick = { open = true }, color = Color.Transparent, shape = CircleShape, modifier = Modifier.size(36.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(AppIcons.More, "More actions", Modifier.size(20.dp), tint = Muted) }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (onEdit != null) DropdownMenuItem(text = { Text("Edit") }, onClick = { open = false; onEdit() })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { open = false; onDelete() })
        }
    }
}
