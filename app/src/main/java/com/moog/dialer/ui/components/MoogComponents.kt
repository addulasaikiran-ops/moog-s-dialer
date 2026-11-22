package com.moog.dialer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moog.dialer.ui.theme.MoogColors

@Composable
fun MoogGroupedSection(
    title: String? = null,
    footer: String? = null,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MoogColors.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MoogColors.surface)
        ) { content() }
        footer?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MoogColors.secondaryLabel,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
fun MoogListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingValue: String? = null,
    destructive: Boolean = false,
    showChevron: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (destructive) MoogColors.red else MoogColors.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.let {
                Text(it, color = MoogColors.secondaryLabel, style = MaterialTheme.typography.bodySmall)
            }
        }
        trailingValue?.let {
            Text(it, color = MoogColors.secondaryLabel, style = MaterialTheme.typography.bodyMedium)
        }
        trailing?.invoke()
        if (showChevron) Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MoogColors.secondaryLabel)
    }
}

@Composable
fun MoogSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    onCancel: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MoogColors.fill)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Search, contentDescription = "Search", tint = MoogColors.secondaryLabel)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MoogColors.label),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, color = MoogColors.secondaryLabel, style = MaterialTheme.typography.bodyLarge)
                inner()
            }
        )
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }) {
                Icon(Icons.Default.Close, contentDescription = "Clear search")
            }
        }
        if (onCancel != null) TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
fun MoogSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier.clip(RoundedCornerShape(9.dp)).background(MoogColors.fill).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Surface(
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 36.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelected(index)
                    },
                color = if (selected) MoogColors.surface else Color.Transparent,
                shadowElevation = if (selected) 1.dp else 0.dp
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text(option, color = MoogColors.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun MoogSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier
    )
}

@Composable
fun MoogAvatar(initials: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(MoogColors.fill),
        contentAlignment = Alignment.Center
    ) {
        Text(initials.ifBlank { "?" }, color = MoogColors.label, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.30f).sp)
    }
}

@Composable
fun MoogCircleButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    background: Color = MoogColors.fill,
    tint: Color = MoogColors.blue,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        modifier = modifier.size(52.dp).clip(CircleShape).clickable {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        shape = CircleShape,
        color = background
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = tint)
        }
    }
}

@Composable
fun MoogEmptyState(
    title: String,
    message: String,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = MoogColors.secondaryLabel, modifier = Modifier.size(34.dp)) }
        Text(title, color = MoogColors.label, style = MaterialTheme.typography.titleMedium)
        Text(message, color = MoogColors.secondaryLabel, style = MaterialTheme.typography.bodyMedium)
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
fun MoogNavBar(title: String, onBack: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) { Text("‹ $title", color = MoogColors.blue, style = MaterialTheme.typography.bodyLarge) }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun MoogTabBar(
    items: List<MoogTabItem>,
    selectedKey: String,
    onSelect: (String) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth().background(MoogColors.surface).padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEach { item ->
            val selected = item.key == selectedKey
            Column(
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(item.key)
                    }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(item.icon, contentDescription = item.label, tint = if (selected) MoogColors.blue else MoogColors.secondaryLabel)
                Text(item.label, color = if (selected) MoogColors.blue else MoogColors.secondaryLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

data class MoogTabItem(val key: String, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoogConfirmSheet(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MoogColors.label)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MoogColors.secondaryLabel)
            TextButton(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                Text(confirmLabel, color = MoogColors.red, style = MaterialTheme.typography.bodyLarge)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}
