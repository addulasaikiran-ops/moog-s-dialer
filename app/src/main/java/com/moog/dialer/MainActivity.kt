package com.moog.dialer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.app.role.RoleManager
import android.os.Build
import android.provider.CallLog
import android.text.format.DateUtils
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import android.provider.ContactsContract
import com.moog.dialer.data.PhoneContact
import com.moog.dialer.data.T9Search
import com.moog.dialer.data.CallLogRepository
import com.moog.dialer.data.RecentCall

private val Page = Color(0xFFF5F6F8)
private val Card = Color(0xFFFFFFFF)
private val Ink = Color(0xFF17191D)
private val Secondary = Color(0xFF858B96)
private val Hairline = Color(0xFFE8EAF0)
private val Blue = Color(0xFF087CFA)
private val SoftBlue = Color(0xFFEAF3FF)

private enum class Tab(val label: String) {
    Favorites("Favorites"), Recents("Recents"), Contacts("Contacts"), Keypad("Keypad"), Settings("Settings")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Blue,
                    onPrimary = Color.White,
                    background = Page,
                    surface = Card,
                    onSurface = Ink,
                    onBackground = Ink
                )
            ) {
                MoogsDialerApp(
                    onDial = { value ->
                        val number = value.trim()
                        if (number.isNotEmpty()) {
                            startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MoogsDialerApp(onDial: (String) -> Unit, contactsViewModel: ContactsViewModel = viewModel()) {
    var selectedTab by remember { mutableStateOf(Tab.Keypad) }
    var number by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var selectedContactId by remember { mutableStateOf<Long?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var hasContactsPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED)
    }
    val contacts by contactsViewModel.contacts.collectAsState()
    val loading by contactsViewModel.loading.collectAsState()
    val isDefaultDialer = remember(context) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_DIALER) == true
    }
    var hasCallLogPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED)
    }
    var recentCalls by remember { mutableStateOf<List<RecentCall>>(emptyList()) }
    var loadingRecentCalls by remember { mutableStateOf(false) }
    val favorites = remember(context) {
        val saved = context.getSharedPreferences("moogs_dialer_preferences", 0)
            .getStringSet("favorite_contact_ids", emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
        mutableStateListOf<Long>().apply { addAll(saved) }
    }

    val callLogPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCallLogPermission = granted
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasContactsPermission = granted
        if (granted) contactsViewModel.refresh()
    }

    LaunchedEffect(hasContactsPermission) {
        if (hasContactsPermission) contactsViewModel.refresh()
    }

    LaunchedEffect(selectedTab, isDefaultDialer, hasCallLogPermission) {
        if (selectedTab == Tab.Recents && isDefaultDialer && hasCallLogPermission) {
            loadingRecentCalls = true
            recentCalls = CallLogRepository(context.contentResolver).getRecentCalls()
            loadingRecentCalls = false
        }
    }

    Column(Modifier.fillMaxSize().background(Page)) {
        if (selectedContactId == null) {
            AppHeader(
                onSearch = { selectedTab = Tab.Contacts },
                onSettings = { selectedTab = Tab.Settings }
            )
        }
        AnimatedContent(
            targetState = selectedTab,
            modifier = Modifier.weight(1f),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "main-tab"
        ) { tab ->
            when (tab) {
                Tab.Keypad -> KeypadScreen(
                    number = number,
                    contacts = if (hasContactsPermission) contacts else emptyList(),
                    onNumberChange = { number = it },
                    onDelete = { if (number.isNotEmpty()) number = number.dropLast(1) },
                    onDial = { onDial(number) },
                    onChooseContact = { number = it }
                )
                Tab.Contacts -> if (selectedContactId != null) {
                    ContactDetailScreen(
                        contactNumbers = contacts.filter { it.id == selectedContactId },
                        onBack = { selectedContactId = null },
                        onDial = onDial
                    )
                } else ContactsScreen(
                    contacts = contacts,
                    loading = loading,
                    hasPermission = hasContactsPermission,
                    query = search,
                    favorites = favorites,
                    onQueryChange = { search = it },
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                    onOpenContact = { selectedContactId = it },
                    onToggleFavorite = { id ->
                        if (favorites.contains(id)) favorites.remove(id) else favorites.add(id)
                        context.getSharedPreferences("moogs_dialer_preferences", 0)
                            .edit()
                            .putStringSet("favorite_contact_ids", favorites.map { it.toString() }.toSet())
                            .apply()
                    },
                    onDial = onDial
                )
                Tab.Favorites -> FavoritesScreen(
                    contacts = contacts.filter { favorites.contains(it.id) },
                    hasPermission = hasContactsPermission,
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                    onOpenContact = { selectedContactId = it },
                    onDial = onDial
                )
                Tab.Recents -> RecentsScreen(
                    calls = recentCalls,
                    loading = loadingRecentCalls,
                    isDefaultDialer = isDefaultDialer,
                    hasPermission = hasCallLogPermission,
                    onRequestPermission = { callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG) },
                    onDial = onDial
                )
                Tab.Settings -> SettingsScreen()
            }
        }
        if (selectedContactId == null) {
            BottomTabs(selected = selectedTab, onSelect = {
                selectedTab = it
                selectedContactId = null
                search = ""
            })
        }
    }
}

@Composable
private fun AppHeader(onSearch: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().background(Card).padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("moog's dialer", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp)
            Text("A BETTER WAY TO CONNECT", color = Secondary, fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium)
        }
        HeaderButton(Icons.Default.Search, "Search", onSearch)
        Spacer(Modifier.width(8.dp))
        HeaderButton(Icons.Default.Settings, "Settings", onSettings)
    }
}

@Composable
private fun HeaderButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Page).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = label, tint = Ink, modifier = Modifier.size(19.dp)) }
}

@Composable
private fun KeypadScreen(
    number: String,
    contacts: List<PhoneContact>,
    onNumberChange: (String) -> Unit,
    onDelete: () -> Unit,
    onDial: () -> Unit,
    onChooseContact: (String) -> Unit
) {
    val keys = listOf(
        listOf("1" to "", "2" to "ABC", "3" to "DEF"),
        listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
        listOf("*" to "", "0" to "+", "#" to "")
    )
    val suggestions = remember(number, contacts) {
        if (number.isBlank()) emptyList()
        else contacts.filter { T9Search.matches(it, number) }.take(3)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(20.dp))
        Text("KEYPAD", color = Secondary, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                BasicTextField(
                    value = number,
                    onValueChange = { onNumberChange(it.filter { c -> c.isDigit() || c == '+' || c == '*' || c == '#' }.take(24)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    textStyle = TextStyle(color = Ink, fontSize = 30.sp, textAlign = TextAlign.Center, letterSpacing = 1.sp),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (number.isEmpty()) Text("Enter a number", color = Secondary.copy(alpha = 0.65f), fontSize = 23.sp)
                            inner()
                        }
                    }
                )
            }
            if (number.isNotEmpty()) {
                IconButton(onClick = onDelete) { Icon(Icons.Default.Backspace, "Delete", tint = Secondary) }
            }
        }
        if (suggestions.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Card)) {
                suggestions.forEach { contact ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onChooseContact(contact.phoneNumber) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(contact.displayName)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(contact.displayName, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(contact.phoneNumber, color = Secondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            keys.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { (digit, letters) ->
                        Column(
                            Modifier.weight(1f).height(66.dp).clip(CircleShape).background(Card)
                                .clickable { onNumberChange(number + digit) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(digit, color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Normal)
                            if (letters.isNotEmpty()) Text(letters, color = Secondary, fontSize = 8.sp, letterSpacing = 1.5.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier.size(62.dp).clip(CircleShape).background(Blue).clickable(onClick = onDial),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Call, contentDescription = "Open phone dialer", tint = Color.White, modifier = Modifier.size(25.dp)) }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ContactsScreen(
    contacts: List<PhoneContact>,
    loading: Boolean,
    hasPermission: Boolean,
    query: String,
    favorites: List<Long>,
    onQueryChange: (String) -> Unit,
    onRequestPermission: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onOpenContact: (Long) -> Unit,
    onDial: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ScreenTitle("Contacts", "Your people, all in one place")
        if (!hasPermission) {
            PermissionCard(
                title = "Connect your contacts",
                body = "Allow access to show and search the contacts stored on this phone. Your contacts stay on your device.",
                button = "Allow contacts access",
                onClick = onRequestPermission
            )
        } else {
            SearchField(query, onQueryChange, "Search by name or number")
            Spacer(Modifier.height(12.dp))
            val filtered = contacts.filter { T9Search.matches(it, query) }
                .groupBy { it.id }
                .values
                .map { it.first() }
            when {
                loading -> CenterMessage("Loading contacts…", "Your address book is being read on this device.")
                filtered.isEmpty() -> CenterMessage(
                    if (query.isBlank()) "No phone contacts found" else "No matches found",
                    if (query.isBlank()) "Contacts with phone numbers will appear here." else "Try another name or number."
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(filtered, key = { it.id }) { contact ->
                        ContactRow(
                            contact = contact,
                            favorite = favorites.contains(contact.id),
                            onFavorite = { onToggleFavorite(contact.id) },
                            onOpenContact = { onOpenContact(contact.id) },
                            onDial = { onDial(contact.phoneNumber) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesScreen(
    contacts: List<PhoneContact>,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenContact: (Long) -> Unit,
    onDial: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ScreenTitle("Favorites", "The people you reach most")
        when {
            !hasPermission -> PermissionCard(
                "Your favorites start with contacts",
                "Allow contacts access to choose people for your favorites.",
                "Allow contacts access",
                onRequestPermission
            )
            contacts.isEmpty() -> CenterMessage("No favorites yet", "Open Contacts and tap the star beside someone to add them here.")
            else -> LazyColumn {
                items(contacts.groupBy { it.id }.values.map { it.first() }, key = { it.id }) { contact ->
                    ContactRow(contact, favorite = true, onFavorite = {}, onOpenContact = { onOpenContact(contact.id) }, onDial = { onDial(contact.phoneNumber) }, showFavorite = false)
                }
            }
        }
    }
}

@Composable
private fun ContactDetailScreen(
    contactNumbers: List<PhoneContact>,
    onBack: () -> Unit,
    onDial: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val contact = contactNumbers.firstOrNull()

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back to contacts", tint = Blue) }
            Text("Contact", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        if (contact == null) {
            CenterMessage("Contact unavailable", "This contact may have been removed from your address book.")
        } else {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(contact.displayName)
                Spacer(Modifier.height(12.dp))
                Text(contact.displayName, color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(if (contactNumbers.size == 1) "1 phone number" else "${contactNumbers.size} phone numbers", color = Secondary, fontSize = 13.sp)
            }
            Text("PHONE NUMBERS", color = Secondary, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(9.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(contactNumbers, key = { "${it.id}-${it.phoneNumber}" }) { phone ->
                    val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.resources, phone.phoneType, null).toString()
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(horizontal = 15.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label, color = Secondary, fontSize = 11.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(phone.phoneNumber, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                        IconButton(onClick = { onDial(phone.phoneNumber) }) { Icon(Icons.Default.Call, contentDescription = "Call ${phone.phoneNumber}", tint = Blue) }
                    }
                }
            }
        }
    }
}
@Composable
private fun RecentsScreen(
    calls: List<RecentCall>,
    loading: Boolean,
    isDefaultDialer: Boolean,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onDial: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ScreenTitle("Recents", "Your recent calls")
        when {
            !isDefaultDialer -> CenterMessage(
                "Default Phone app required",
                "Android protects call history. MOOG'S DIALER can show it only after it is eligible for and selected as the default Phone app. Incoming and ongoing call screens are still part of that setup."
            )
            !hasPermission -> PermissionCard(
                "Allow call history access",
                "Grant call history permission to show recent incoming, outgoing, and missed calls on this device.",
                "Allow call history",
                onRequestPermission
            )
            loading -> CenterMessage("Loading recent calls…", "Reading call history stored on this device.")
            calls.isEmpty() -> CenterMessage("No recent calls", "Your incoming, outgoing, and missed calls will appear here.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(calls, key = { it.id }) { call ->
                    RecentCallRow(call = call, onDial = { onDial(call.number) })
                }
            }
        }
    }
}

@Composable
private fun RecentCallRow(call: RecentCall, onDial: () -> Unit) {
    val callType = when (call.type) {
        CallLog.Calls.INCOMING_TYPE -> "Incoming"
        CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
        CallLog.Calls.MISSED_TYPE -> "Missed"
        CallLog.Calls.REJECTED_TYPE -> "Rejected"
        CallLog.Calls.BLOCKED_TYPE -> "Blocked"
        CallLog.Calls.VOICEMAIL_TYPE -> "Voicemail"
        else -> "Call"
    }
    val callColor = if (call.type == CallLog.Calls.MISSED_TYPE || call.type == CallLog.Calls.REJECTED_TYPE) {
        Color(0xFFD94B4B)
    } else Blue
    val relativeTime = DateUtils.getRelativeTimeSpanString(
        call.date,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card)
            .clickable(onClick = onDial).padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(SoftBlue), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Call, contentDescription = null, tint = callColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(call.displayName.ifBlank { call.number.ifBlank { "Unknown number" } }, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text("${callType} · ${relativeTime}", color = callColor, fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (call.durationSeconds > 0) {
                Text("${call.durationSeconds / 60}:${(call.durationSeconds % 60).toString().padStart(2, '0')}", color = Secondary, fontSize = 11.sp)
            }
            IconButton(onClick = onDial) {
                Icon(Icons.Default.Call, contentDescription = "Call ${call.number}", tint = Blue, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ScreenTitle("Settings", "Make calling feel like yours")
        SettingsRow("Appearance", "iPhone-inspired light interface")
        SettingsRow("Contacts", "On-device contact access")
        SettingsRow("Calling", "System dialer handoff is enabled")
        SettingsRow("Privacy", "No account or cloud contact sync")
        SettingsRow("About", "MOOG'S DIALER · Version 0.2.0")
        Spacer(Modifier.height(18.dp))
        Text("BUILT FOR YOUR EVERYDAY CONNECTIONS", color = Blue, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("This app reads contacts only after you grant permission. Call history and full in-call controls are planned for the Telecom integration milestone.", color = Secondary, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun ScreenTitle(title: String, subtitle: String) {
    Spacer(Modifier.height(14.dp))
    Text(title, color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.9).sp)
    Spacer(Modifier.height(3.dp))
    Text(subtitle, color = Secondary, fontSize = 13.sp)
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFE9EBEF)).padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = Secondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(color = Ink, fontSize = 14.sp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, color = Secondary, fontSize = 14.sp)
                    inner()
                }
            }
        )
    }
}

@Composable
private fun ContactRow(
    contact: PhoneContact,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onOpenContact: () -> Unit,
    onDial: () -> Unit,
    showFavorite: Boolean = true
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card).clickable(onClick = onOpenContact).padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(contact.displayName)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(contact.displayName, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(contact.phoneNumber, color = Secondary, fontSize = 12.sp)
        }
        if (showFavorite) {
            IconButton(onClick = onFavorite) {
                Icon(Icons.Default.Favorite, contentDescription = if (favorite) "Remove favorite" else "Add favorite",
                    tint = if (favorite) Blue else Secondary.copy(alpha = 0.6f), modifier = Modifier.size(19.dp))
            }
        }
        IconButton(onClick = onDial) {
            Icon(Icons.Default.Call, contentDescription = "Call ${contact.displayName}", tint = Blue, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun Avatar(name: String) {
    val initials = name.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("").uppercase()
    Box(Modifier.size(42.dp).clip(CircleShape).background(SoftBlue), contentAlignment = Alignment.Center) {
        Text(initials.ifBlank { "?" }, color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PermissionCard(title: String, body: String, button: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).padding(20.dp)) {
        Icon(Icons.Default.Contacts, contentDescription = null, tint = Blue, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Secondary, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onClick, shape = RoundedCornerShape(12.dp)) { Text(button) }
    }
}

@Composable
private fun CenterMessage(title: String, body: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(horizontal = 30.dp).offset(y = (-24).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(62.dp).clip(CircleShape).background(SoftBlue), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Blue, modifier = Modifier.size(27.dp))
            }
            Spacer(Modifier.height(15.dp))
            Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(body, color = Secondary, fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SettingsRow(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 15.dp)) {
        Text(title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(detail, color = Secondary, fontSize = 12.sp)
    }
    HorizontalDivider(color = Hairline, thickness = 0.8.dp)
}

@Composable
private fun BottomTabs(selected: Tab, onSelect: (Tab) -> Unit) {
    val tabItems = listOf(
        Tab.Favorites to Icons.Default.Favorite,
        Tab.Recents to Icons.Default.History,
        Tab.Contacts to Icons.Default.Person,
        Tab.Keypad to Icons.Default.Contacts,
        Tab.Settings to Icons.Default.Settings
    )
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().background(Card).padding(horizontal = 5.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabItems.forEach { (tab, icon) ->
            val active = selected == tab
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onSelect(tab) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, contentDescription = tab.label, tint = if (active) Blue else Secondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(3.dp))
                Text(tab.label, color = if (active) Blue else Secondary, fontSize = 9.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}
