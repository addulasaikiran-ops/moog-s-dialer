package com.moog.dialer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Canvas = Color(0xFF0D100D)
private val SurfaceCard = Color(0xFF191E18)
private val SurfaceRaised = Color(0xFF242B22)
private val Accent = Color(0xFFC5F477)
private val PrimaryText = Color(0xFFF2F4EE)
private val SecondaryText = Color(0xFF9AA394)
private val Hairline = Color(0xFF30382D)

private enum class Destination(val label: String) {
    Recents("Recents"), Contacts("Contacts"), Keypad("Keypad"), Favorites("Favorites"), Settings("Settings")
}

private data class CallEntry(val name: String, val number: String, val detail: String, val initials: String)
private data class ContactEntry(val name: String, val number: String, val initials: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Canvas,
                    surface = SurfaceCard,
                    primary = Accent,
                    onPrimary = Canvas,
                    onBackground = PrimaryText,
                    onSurface = PrimaryText
                )
            ) {
                MoogsDialerApp(
                    onDial = { number ->
                        if (number.isNotBlank()) {
                            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MoogsDialerApp(onDial: (String) -> Unit) {
    var destination by remember { mutableStateOf(Destination.Keypad) }
    var number by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }

    // Live provider data is intentionally not fabricated; provider integration is the next milestone.
    val recents = emptyList<CallEntry>()
    val contacts = emptyList<ContactEntry>()

    Column(Modifier.fillMaxSize().background(Canvas)) {
        AppHeader(
            onSettings = { destination = Destination.Settings },
            onSearch = { destination = Destination.Contacts }
        )

        AnimatedContent(
            targetState = destination,
            modifier = Modifier.weight(1f),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "dialer-destination"
        ) { current ->
            when (current) {
                Destination.Keypad -> KeypadScreen(
                    number = number,
                    onNumberChange = { number = it },
                    onDial = { onDial(number) },
                    onDelete = { if (number.isNotEmpty()) number = number.dropLast(1) },
                    onClear = { number = "" }
                )
                Destination.Recents -> ListScreen(
                    title = "Your calls",
                    subtitle = "The people you've connected with",
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "Search recent calls",
                    emptyTitle = "No recent calls yet",
                    emptyBody = "Your call activity will appear here when call history is connected.",
                    rows = recents.filter { it.name.contains(query, true) || it.number.contains(query) }.map {
                        PersonRow(it.name, it.number, it.detail, it.initials) { number = it.number; destination = Destination.Keypad }
                    }
                )
                Destination.Contacts -> ListScreen(
                    title = "People",
                    subtitle = "Your circle, all in one place",
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "Search people",
                    emptyTitle = "No matching contacts",
                    emptyBody = "Try another name or number.",
                    rows = contacts.filter { it.name.contains(query, true) || it.number.contains(query) }.map {
                        PersonRow(it.name, it.number, "Contact", it.initials) { number = it.number; destination = Destination.Keypad }
                    }
                )
                Destination.Favorites -> ListScreen(
                    title = "Favorites",
                    subtitle = "Your people, one tap away",
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "Search favorites",
                    emptyTitle = "Make room for your favorites",
                    emptyBody = "Favorite contacts will be easy to reach from this screen.",
                    rows = emptyList()
                )
                Destination.Settings -> SettingsScreen()
            }
        }

        BottomNavigation(destination = destination, onSelect = {
            destination = it
            query = ""
        })
    }
}

@Composable
private fun AppHeader(onSettings: () -> Unit, onSearch: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 22.dp, end = 16.dp, top = 18.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("moog's dialer", color = PrimaryText, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp)
            Text("CALL YOUR WORLD", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp)
        }
        HeaderAction(Icons.Default.Search, "Search contacts", onSearch)
        Spacer(Modifier.width(8.dp))
        HeaderAction(Icons.Default.Settings, "Settings", onSettings)
    }
}

@Composable
private fun HeaderAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(SurfaceCard).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = label, tint = SecondaryText, modifier = Modifier.size(19.dp)) }
}

@Composable
private fun KeypadScreen(
    number: String,
    onNumberChange: (String) -> Unit,
    onDial: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit
) {
    val keys = listOf(
        listOf("1" to "", "2" to "ABC", "3" to "DEF"),
        listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
        listOf("*" to "", "0" to "+", "#" to "")
    )

    Column(
        Modifier.fillMaxSize().padding(horizontal = 25.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.35f))
        Text("READY WHEN YOU ARE", color = SecondaryText, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.2.sp)
        Spacer(Modifier.height(13.dp))
        Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                BasicTextField(
                    value = number,
                    onValueChange = { onNumberChange(it.filter { c -> c.isDigit() || c == '+' || c == '*' || c == '#' }.take(24)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    textStyle = TextStyle(color = PrimaryText, fontSize = 30.sp, textAlign = TextAlign.Center, letterSpacing = 1.sp),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (number.isEmpty()) Text("Enter a number", color = SecondaryText.copy(alpha = 0.55f), fontSize = 23.sp)
                            inner()
                        }
                    }
                )
            }
            if (number.isNotEmpty()) {
                IconButton(onClick = onDelete) { Icon(Icons.Default.Backspace, "Delete", tint = SecondaryText) }
                Text("Clear", color = Accent, fontSize = 11.sp, modifier = Modifier.clickable(onClick = onClear).padding(start = 4.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            keys.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { (digit, letters) ->
                        Column(
                            Modifier.weight(1f).height(67.dp).clip(RoundedCornerShape(22.dp))
                                .background(SurfaceCard).clickable { onNumberChange(number + digit) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(digit, color = PrimaryText, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                            if (letters.isNotEmpty()) Text(letters, color = SecondaryText, fontSize = 8.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(Accent).clickable(onClick = onDial),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Call, contentDescription = "Open system dialer", tint = Canvas, modifier = Modifier.size(27.dp)) }
        Spacer(Modifier.weight(0.45f))
    }
}

@Composable
private fun ListScreen(
    title: String,
    subtitle: String,
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    emptyTitle: String,
    emptyBody: String,
    rows: List<@Composable () -> Unit>
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Text(title, color = PrimaryText, fontSize = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = SecondaryText, fontSize = 12.sp)
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SurfaceCard).padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = SecondaryText, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query, onValueChange = onQueryChange, singleLine = true,
                textStyle = TextStyle(color = PrimaryText, fontSize = 14.sp),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) Text(placeholder, color = SecondaryText, fontSize = 14.sp)
                        inner()
                    }
                }
            )
        }
        Spacer(Modifier.height(14.dp))
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 55.dp)) {
                    Box(Modifier.size(62.dp).clip(CircleShape).background(SurfaceCard), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Contacts, null, tint = Accent, modifier = Modifier.size(25.dp))
                    }
                    Spacer(Modifier.height(15.dp))
                    Text(emptyTitle, color = PrimaryText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(emptyBody, color = SecondaryText, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(rows.size) { index -> rows[index]() }
            }
        }
    }
}

@Composable
private fun PersonRow(name: String, number: String, detail: String, initials: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 11.dp, horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(SurfaceRaised), contentAlignment = Alignment.Center) {
            Text(initials, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text("$number  ·  $detail", color = SecondaryText, fontSize = 11.sp)
        }
        Icon(Icons.Default.Call, contentDescription = "Select number", tint = Accent, modifier = Modifier.padding(10.dp).size(18.dp))
    }
    HorizontalDivider(color = Hairline.copy(alpha = 0.65f), thickness = 0.6.dp)
}

@Composable
private fun SettingsScreen() {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Text("A little personal", color = PrimaryText, fontSize = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
        Spacer(Modifier.height(4.dp))
        Text("Make your calling experience yours", color = SecondaryText, fontSize = 12.sp)
        Spacer(Modifier.height(24.dp))
        SettingRow("Appearance", "Graphite and lime · Signature theme")
        SettingRow("Calling", "System phone app handoff")
        SettingRow("Privacy", "Permissions and data controls")
        SettingRow("About moog's dialer", "Version 0.1.0 · Built with care")
        Spacer(Modifier.height(18.dp))
        Text("MOOG'S DIALER IS A WORK IN PROGRESS", color = Accent, fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Contacts, call history, and full default-phone functionality will be connected in upcoming development milestones.", color = SecondaryText, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun SettingRow(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 15.dp)) {
        Text(title, color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(detail, color = SecondaryText, fontSize = 11.sp)
    }
    HorizontalDivider(color = Hairline, thickness = 0.7.dp)
}

@Composable
private fun BottomNavigation(destination: Destination, onSelect: (Destination) -> Unit) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().background(SurfaceCard).padding(horizontal = 5.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val items = listOf(
            Destination.Recents to Icons.Default.History,
            Destination.Contacts to Icons.Default.Contacts,
            Destination.Keypad to Icons.Default.Call,
            Destination.Favorites to Icons.Default.Star,
            Destination.Settings to Icons.Default.MoreHoriz
        )
        items.forEach { (item, icon) ->
            val selected = destination == item
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(13.dp)).clickable { onSelect(item) }.padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, contentDescription = item.label, tint = if (selected) Accent else SecondaryText, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(3.dp))
                Text(item.label, color = if (selected) Accent else SecondaryText, fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
