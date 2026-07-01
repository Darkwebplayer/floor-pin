package dev.infyplus.floorpin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.infyplus.floorpin.ui.theme.BorderColor
import dev.infyplus.floorpin.ui.theme.Ink
import dev.infyplus.floorpin.ui.theme.Muted
import dev.infyplus.floorpin.ui.theme.White

@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    crumb: String? = null,
    openDrawer: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(White)
            .border(width = 1.dp, color = BorderColor)
            .defaultMinSize(minHeight = 68.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        when {
            onBack != null -> LeadingButton(AppIcons.ChevronLeft, "Back", onBack)
            openDrawer != null -> LeadingButton(AppIcons.Menu, "Menu", openDrawer)
        }
        Column(Modifier.weight(1f)) {
            if (crumb != null) {
                Text(crumb, style = MaterialTheme.typography.bodyMedium, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
private fun LeadingButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(4.dp), color = White, modifier = Modifier.size(42.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, desc, Modifier.size(22.dp), tint = Ink) }
    }
}
