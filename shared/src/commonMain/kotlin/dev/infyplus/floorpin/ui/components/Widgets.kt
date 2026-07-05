package dev.infyplus.floorpin.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.infyplus.floorpin.domain.IssueStatus
import dev.infyplus.floorpin.domain.SelectOption
import dev.infyplus.floorpin.ui.theme.Accent
import dev.infyplus.floorpin.ui.theme.BorderColor
import dev.infyplus.floorpin.ui.theme.Ink2
import dev.infyplus.floorpin.ui.theme.LocalFloorPinColors
import dev.infyplus.floorpin.ui.theme.Muted
import dev.infyplus.floorpin.ui.theme.SurfaceWarm
import dev.infyplus.floorpin.ui.theme.White

enum class ButtonVariant { Primary, Neutral, Ghost, Danger }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    small: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val fp = LocalFloorPinColors.current
    val bg = when (variant) {
        ButtonVariant.Primary -> Accent
        ButtonVariant.Neutral -> White
        ButtonVariant.Ghost -> Color.Transparent
        ButtonVariant.Danger -> Color.Transparent
    }
    val fg = when (variant) {
        ButtonVariant.Primary -> White
        ButtonVariant.Neutral -> Ink2
        ButtonVariant.Ghost -> Accent
        ButtonVariant.Danger -> fp.statusOpen
    }
    val border = when (variant) {
        ButtonVariant.Neutral -> BorderStroke(1.dp, BorderColor)
        ButtonVariant.Ghost -> BorderStroke(1.dp, Color(0xFFB9B9F9))
        ButtonVariant.Danger -> BorderStroke(1.dp, fp.statusOpen.copy(alpha = 0.3f))
        ButtonVariant.Primary -> null
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(4.dp),
        color = bg,
        contentColor = fg,
        border = border,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier
                .defaultMinSize(minHeight = if (small) 32.dp else 40.dp)
                .padding(horizontal = if (small) 12.dp else 16.dp, vertical = if (small) 5.dp else 9.dp),
        ) {
            if (leadingIcon != null) Icon(leadingIcon, null, Modifier.size(16.dp))
            Text(
                text,
                style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun StatusBadge(status: IssueStatus, modifier: Modifier = Modifier, count: Int? = null) {
    val fp = LocalFloorPinColors.current
    val hue = when (status) {
        IssueStatus.OPEN -> fp.statusOpen
        IssueStatus.IN_PROGRESS -> fp.statusProgress
        IssueStatus.RESOLVED -> fp.statusResolved
        IssueStatus.CLOSED -> fp.muted
    }
    val label = if (count != null) "$count ${status.label.lowercase()}" else status.label
    Badge(text = label, dotColor = hue, bg = hue.copy(alpha = 0.12f), fg = hue, modifier = modifier)
}

@Composable
fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    dotColor: Color? = null,
    bg: Color = SurfaceWarm,
    fg: Color = Ink2,
) {
    Surface(shape = RoundedCornerShape(4.dp), color = bg, contentColor = fg, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
        ) {
            if (dotColor != null) Box(Modifier.size(7.dp).background(dotColor, CircleShape))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = White,
        border = BorderStroke(1.dp, BorderColor),
        shadowElevation = if (elevated) 2.dp else 0.dp,
        modifier = modifier,
        content = content,
    )
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    required: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label?.let { { Text(buildString { append(it); if (required) append(" *") }) } },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        isError = isError,
        supportingText = supportingText?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
        shape = RoundedCornerShape(4.dp),
    )
}

@Composable
fun AppDropdown(
    selectedValue: String?,
    options: List<SelectOption>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    required: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label ?: ""

    Box(modifier) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            label = label?.let { { Text(buildString { append(it); if (required) append(" *") }) } },
            trailingIcon = { Text("▼", style = MaterialTheme.typography.labelSmall, color = Muted) },
            shape = RoundedCornerShape(4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = LocalContentColor.current,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = Muted,
            ),
            enabled = false,
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.label) },
                    onClick = { onSelect(opt.value); expanded = false },
                )
            }
        }
    }
}

@Composable
fun Avatar(initials: String, modifier: Modifier = Modifier, size: Int = 34) {
    Box(
        modifier = modifier.size(size.dp).background(Accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides White) {
            Text(initials, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Normal)
        }
    }
}
