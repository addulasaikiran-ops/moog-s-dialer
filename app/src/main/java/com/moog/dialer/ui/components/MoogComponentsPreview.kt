package com.moog.dialer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.moog.dialer.ui.theme.MoogTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Star

@Composable
private fun MoogComponentGallery() {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(0) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MoogSearchBar(value = query, onValueChange = { query = it })
        MoogSegmentedControl(
            options = listOf("All", "Missed"),
            selectedIndex = selected,
            onSelected = { selected = it },
            modifier = Modifier.fillMaxWidth()
        )
        MoogGroupedSection(title = "FAVORITES", footer = "Your favorite contacts appear here.") {
            MoogListRow(
                title = "Alex Morgan",
                subtitle = "Mobile · 555 0100",
                leading = { MoogAvatar("AM") },
                trailing = { MoogCircleButton(Icons.Default.Call, "Call Alex", onClick = {}) },
                showChevron = true,
                onClick = {}
            )
            MoogListRow(
                title = "Taylor Reed",
                subtitle = "Work · 555 0120",
                leading = { MoogAvatar("TR") },
                trailing = { MoogCircleButton(Icons.Default.Star, "Favorite Taylor", onClick = {}) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MoogTabBar(
                items = listOf(
                    MoogTabItem("favorites", "Favorites", Icons.Default.Star),
                    MoogTabItem("calls", "Calls", Icons.Default.Call)
                ),
                selectedKey = "favorites",
                onSelect = {}
            )
        }
        MoogEmptyState(title = "No recent calls", message = "Calls will appear here when available.")
    }
}

@Preview(name = "MOOG components · Light", showBackground = true)
@Composable
private fun MoogComponentsLightPreview() {
    MoogTheme {
        MoogComponentGallery()
    }
}

@Preview(name = "MOOG components · Dark", showBackground = true)
@Composable
private fun MoogComponentsDarkPreview() {
    MoogTheme(forceDark = true) {
        MoogComponentGallery()
    }
}

@Preview(name = "MOOG components · 1.3x text", showBackground = true, fontScale = 1.3f)
@Composable
private fun MoogComponentsLargeTextPreview() {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
        MoogTheme {
            MoogComponentGallery()
        }
    }
}
