package com.moog.dialer.ui.screens

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.app.role.RoleManager
import android.telecom.TelecomManager
import android.os.Build
import android.provider.CallLog
import android.provider.Settings
import android.text.format.DateUtils
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.stickyHeader
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.moog.dialer.data.PhoneAccountOption
import com.moog.dialer.data.PhoneAccountRepository
import com.moog.dialer.ui.theme.MoogColors
import com.moog.dialer.ui.theme.MoogTheme
import com.moog.dialer.ui.theme.MoogAppearanceMode
import com.moog.dialer.ui.theme.MoogAppearancePreferences
import com.moog.dialer.ui.components.MoogTabBar
import com.moog.dialer.ui.components.MoogGroupedSection
import com.moog.dialer.ui.components.MoogSegmentedControl
import com.moog.dialer.ui.components.MoogTabItem
import com.moog.dialer.ContactsViewModel
import com.moog.dialer.SpamBlockRepository
import com.moog.dialer.SpamProtectionActivity
import com.moog.dialer.VoicemailActivity
import com.moog.dialer.isMoogDefaultPhoneApp

private enum class Tab(val label: String) {
    Favorites("Favorites"), Recents("Recents"), Contacts("Contacts"), Keypad("Keypad"), Settings("Settings")
}

@Composable
fun MoogsDialerApp(initialNumber: String, onDial: (String) -> Unit, contactsViewModel: ContactsViewModel = viewModel()) {
    var selectedTab by remember { mutableStateOf(Tab.Keypad) }
    var number by remember { mutableStateOf(initialNumber) }
    var search by remember { mutableStateOf("") }
    var selectedContactId by remember { mutableStateOf<Long?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    BackHandler(enabled = selectedContactId != null) { selectedContactId = null }
    var hasContactsPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED)
    }
    val contacts by contactsViewModel.contacts.collectAsState()
    val loading by contactsViewModel.loading.collectAsState()
    var isDefaultDialer by remember {
        mutableStateOf(
            isMoogDefaultPhoneApp(context)
        )
    }
    var hasPhoneStatePermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) }
    var hasPhoneNumbersPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED) }
    var phoneAccounts by remember { mutableStateOf<List<PhoneAccountOption>>(emptyList()) }
    var selectedPhoneAccountKey by remember { mutableStateOf(PhoneAccountRepository.getPreferredAccountKey(context)) }
    var hasCallLogPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED)
    }
    var hasNotificationPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    var canUseFullScreenIntent by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 34 ||
                context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        )
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

    val phoneStatePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        hasPhoneStatePermission = permissions[Manifest.permission.READ_PHONE_STATE] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        hasPhoneNumbersPermission = permissions[Manifest.permission.READ_PHONE_NUMBERS] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
        if (hasPhoneStatePermission && hasPhoneNumbersPermission) phoneAccounts = PhoneAccountRepository.getCallAccounts(context)
    }

    val callLogPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCallLogPermission = granted
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }
    val phoneRoleLauncher = rememberLauncherForActivityResult(StartActivityForResult()) {
        isDefaultDialer = isMoogDefaultPhoneApp(context)
    }
    val fullScreenIntentLauncher = rememberLauncherForActivityResult(StartActivityForResult()) {
        canUseFullScreenIntent = Build.VERSION.SDK_INT < 34 ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
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

    LaunchedEffect(selectedTab, hasPhoneStatePermission, hasPhoneNumbersPermission) {
        if ((selectedTab == Tab.Settings || selectedTab == Tab.Recents) && hasPhoneStatePermission && hasPhoneNumbersPermission) {
            phoneAccounts = PhoneAccountRepository.getCallAccounts(context)
            val activeKeys = phoneAccounts.map { it.key }.toSet()
            if (selectedPhoneAccountKey != null && selectedPhoneAccountKey !in activeKeys) {
                selectedPhoneAccountKey = null
                PhoneAccountRepository.savePreferredAccount(context, null)
            }
        }
        if (selectedTab == Tab.Recents) {
            isDefaultDialer = isMoogDefaultPhoneApp(context)
        }
    }

    LaunchedEffect(selectedTab, selectedContactId) {
        if (selectedTab == Tab.Contacts && selectedContactId != null) {
            isDefaultDialer = isMoogDefaultPhoneApp(context)
        }
    }

    LaunchedEffect(selectedTab, selectedContactId, isDefaultDialer, hasCallLogPermission) {
        val needsCallHistory = selectedTab == Tab.Recents ||
            (selectedTab == Tab.Contacts && selectedContactId != null)
        if (needsCallHistory && isDefaultDialer && hasCallLogPermission) {
            loadingRecentCalls = true
            try {
                recentCalls = CallLogRepository(context.contentResolver, if (hasPhoneStatePermission && hasPhoneNumbersPermission) PhoneAccountRepository.getAccountLabels(context) else emptyMap()).getRecentCalls()
            } catch (_: SecurityException) {
                hasCallLogPermission = false
                recentCalls = emptyList()
            } finally {
                loadingRecentCalls = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MoogColors.background)) {
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
                        recentCalls = recentCalls,
                        onBack = { selectedContactId = null },
                        onDial = onDial,
                        onMessage = { phoneNumber ->
                            if (phoneNumber.isNotBlank()) {
                                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", phoneNumber, null)))
                            }
                        }
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
                Tab.Settings -> SettingsScreen(
                    isDefaultDialer = isDefaultDialer,
                    hasNotificationPermission = hasNotificationPermission,
                    canUseFullScreenIntent = canUseFullScreenIntent,
                    hasPhoneStatePermission = hasPhoneStatePermission && hasPhoneNumbersPermission,
                    phoneAccounts = phoneAccounts,
                    selectedPhoneAccountKey = selectedPhoneAccountKey,
                    onSelectPhoneAccount = { key ->
                        selectedPhoneAccountKey = key
                        PhoneAccountRepository.savePreferredAccount(context, key)
                    },
                    onRequestPhoneState = {
                        phoneStatePermissionLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS))
                    },
                    onRequestPhoneRole = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val manager = context.getSystemService(RoleManager::class.java)
                            if (manager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true) {
                                phoneRoleLauncher.launch(manager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                            }
                        } else {
                            val legacyIntent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
                            }
                            phoneRoleLauncher.launch(legacyIntent)
                        }
                    },
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onRequestFullScreenIntent = {
                        if (Build.VERSION.SDK_INT >= 34) {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            fullScreenIntentLauncher.launch(intent)
                        }
                    },
                    onOpenSpamProtection = {
                        context.startActivity(Intent(context, SpamProtectionActivity::class.java))
                    },
                    onOpenVoicemail = {
                        context.startActivity(Intent(context, VoicemailActivity::class.java))
                    }
                )
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
private fun KeypadScreen(
    number: String,
    contacts: List<PhoneContact>,
    onNumberChange: (String) -> Unit,
    onDelete: () -> Unit,
    onDial: () -> Unit,
    onChooseContact: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptics = LocalHapticFeedback.current
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
    val numberSize = when {
        number.length <= 8 -> 34.sp
        number.length <= 12 -> 30.sp
        number.length <= 16 -> 26.sp
        else -> 22.sp
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        if (suggestions.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { contact ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(MoogColors.surface)
                            .clickable { onChooseContact(contact.phoneNumber) }
                            .padding(horizontal = 9.dp, vertical = 8.dp)
                    ) {
                        Text(
                            contact.displayName,
                            color = MoogColors.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text(
                            contact.phoneNumber,
                            color = MoogColors.secondaryLabel,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Box(
            Modifier.fillMaxWidth().height(62.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = number,
                onValueChange = {
                    onNumberChange(it.filter { c -> c.isDigit() || c == '+' || c == '*' || c == '#' }.take(24))
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                textStyle = TextStyle(
                    color = MoogColors.label,
                    fontSize = numberSize,
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.5.sp
                ),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (number.isEmpty()) {
                            Text(
                                "Enter a number",
                                color = MoogColors.secondaryLabel,
                                fontSize = 22.sp
                            )
                        }
                        inner()
                    }
                }
            )
        }

        if (number.isNotBlank()) {
            TextButton(
                onClick = {
                    val addContact = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                        type = ContactsContract.RawContacts.CONTENT_TYPE
                        putExtra(ContactsContract.Intents.Insert.PHONE, number)
                    }
                    runCatching { context.startActivity(addContact) }
                },
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text("Add Number", color = MoogColors.blue, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Spacer(Modifier.height(44.dp))
        }

        Spacer(Modifier.weight(1f))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            keys.forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { (digit, letters) ->
                        val interactionSource = remember(digit) { MutableInteractionSource() }
                        val pressed by interactionSource.collectIsPressedAsState()
                        val keyColor by animateColorAsState(
                            targetValue = if (pressed) MoogColors.separator else MoogColors.keyFill,
                            label = "key-press"
                        )
                        Box(
                            Modifier.size(78.dp)
                                .clip(CircleShape)
                                .background(keyColor)
                                .combinedClickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                        onNumberChange(number + digit)
                                    },
                                    onLongClick = if (digit == "0") {
                                        {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onNumberChange(number + "+")
                                        }
                                    } else null
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    digit,
                                    color = MoogColors.label,
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Light
                                )
                                if (letters.isNotEmpty()) {
                                    Text(
                                        letters,
                                        color = MoogColors.secondaryLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(86.dp)) {
            Box(
                Modifier.align(Alignment.Center).size(78.dp).clip(CircleShape)
                    .background(MoogColors.green)
                    .clickable(onClick = onDial),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Call,
                    contentDescription = "Call entered number",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
            if (number.isNotEmpty()) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.align(Alignment.CenterEnd).size(52.dp)
                ) {
                    Icon(
                        Icons.Default.Backspace,
                        contentDescription = "Delete last digit",
                        tint = MoogColors.secondaryLabel
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
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
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
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
            val groups = remember(filtered) {
                filtered.groupBy { contact ->
                    contact.displayName.firstOrNull()?.uppercaseChar()
                        ?.takeIf { it in 'A'..'Z' }?.toString() ?: "#"
                }.toSortedMap(compareBy { key -> if (key == "#") "ZZZ" else key })
            }
            val indexPositions = remember(groups) {
                var position = 0
                buildMap {
                    groups.forEach { (letter, members) ->
                        put(letter, position)
                        position += members.size + 1
                    }
                }
            }
            when {
                loading -> CenterMessage("Loading contacts…", "Your address book is being read on this device.")
                filtered.isEmpty() -> CenterMessage(
                    if (query.isBlank()) "No phone contacts found" else "No matches found",
                    if (query.isBlank()) "Contacts with phone numbers will appear here." else "Try another name or number."
                )
                else -> Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(end = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        groups.forEach { (letter, members) ->
                            stickyHeader(key = "section-$letter") {
                                Box(
                                    Modifier.fillMaxWidth().background(MoogColors.background)
                                        .padding(start = 6.dp, top = 8.dp, bottom = 4.dp)
                                ) {
                                    Text(letter, color = MoogColors.secondaryLabel, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            items(members, key = { it.id }) { contact ->
                                ContactRow(
                                    contact = contact,
                                    favorite = favorites.contains(contact.id),
                                    onFavorite = { onToggleFavorite(contact.id) },
                                    onOpenContact = { onOpenContact(contact.id) },
                                    onDial = { onDial(contact.phoneNumber) }
                                )
                            }
                        }
                        item {
                            Text(
                                "${filtered.size} Contacts",
                                color = MoogColors.secondaryLabel,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Column(
                        Modifier.align(Alignment.CenterEnd).padding(end = 1.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        groups.keys.forEach { letter ->
                            Text(
                                letter,
                                color = MoogColors.blue,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    scope.launch { listState.animateScrollToItem(indexPositions[letter] ?: 0) }
                                }.padding(vertical = 1.dp, horizontal = 2.dp)
                            )
                        }
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
    val context = androidx.compose.ui.platform.LocalContext.current
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
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(contacts.groupBy { it.id }.values.map { it.first() }, key = { it.id }) { contact ->
                    val numberLabel = ContactsContract.CommonDataKinds.Phone
                        .getTypeLabel(context.resources, contact.phoneType, null).toString()
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(MoogColors.surface)
                            .clickable { onDial(contact.phoneNumber) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(contact.displayName)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(contact.displayName, color = MoogColors.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(numberLabel, color = MoogColors.secondaryLabel, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { onOpenContact(contact.id) }) {
                            Icon(Icons.Default.Info, contentDescription = "Contact information for ${contact.displayName}", tint = MoogColors.blue)
                        }
                    }
                }
            }
        }
    }
}

private fun normalizedPhoneNumber(number: String): String =
    number.filter { it.isDigit() }.let { digits ->
        if (digits.length > 10) digits.takeLast(10) else digits
    }

@Composable
private fun ContactDetailScreen(
    contactNumbers: List<PhoneContact>,
    recentCalls: List<RecentCall>,
    onBack: () -> Unit,
    onDial: (String) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val contact = contactNumbers.firstOrNull()
    // Keep distinct saved numbers, but do not show duplicate rows when the
    // Contacts Provider returns the same formatted number more than once.
    val uniqueNumbers = remember(contactNumbers) {
        contactNumbers.distinctBy { normalizedPhoneNumber(it.phoneNumber) }
    }
    val numberKeys = remember(uniqueNumbers) { uniqueNumbers.map { normalizedPhoneNumber(it.phoneNumber) }.toSet() }
    val contactHistory = remember(recentCalls, numberKeys) {
        recentCalls.filter { normalizedPhoneNumber(it.number) in numberKeys }
            .sortedByDescending { it.date }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back to contacts", tint = MoogColors.blue) }
            Text("Contact profile", color = MoogColors.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        if (contact == null) {
            CenterMessage("Contact unavailable", "This contact may have been removed from your address book.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MoogColors.surface).padding(vertical = 22.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Avatar(contact.displayName)
                        Spacer(Modifier.height(12.dp))
                        Text(contact.displayName, color = MoogColors.label, fontSize = 25.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text(
                            if (uniqueNumbers.size == 1) "1 saved number" else "${uniqueNumbers.size} saved numbers",
                            color = MoogColors.secondaryLabel,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (contactHistory.isEmpty()) "No recent call history" else "${contactHistory.size} recent call${if (contactHistory.size == 1) "" else "s"}",
                            color = MoogColors.blue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                item {
                    Text("PHONE NUMBERS", color = MoogColors.secondaryLabel, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
                }
                items(uniqueNumbers, key = { "${it.id}-${normalizedPhoneNumber(it.phoneNumber)}" }) { phone ->
                    val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.resources, phone.phoneType, null).toString()
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MoogColors.surface).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(label, color = MoogColors.secondaryLabel, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            Text(phone.phoneNumber, color = MoogColors.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                        IconButton(onClick = { onMessage(phone.phoneNumber) }) {
                            Icon(Icons.Default.Sms, contentDescription = "Message ${label.lowercase()} number", tint = MoogColors.blue)
                        }
                        IconButton(onClick = { onDial(phone.phoneNumber) }) {
                            Icon(Icons.Default.Call, contentDescription = "Call ${label.lowercase()} number", tint = MoogColors.blue)
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("RECENT CALL HISTORY", color = MoogColors.secondaryLabel, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${contactHistory.size}", color = MoogColors.blue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (contactHistory.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MoogColors.surface).padding(16.dp)) {
                            Text("No calls with this contact yet", color = MoogColors.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Calls will appear here when Android makes the device call history available.", color = MoogColors.secondaryLabel, fontSize = 11.sp)
                        }
                    }
                } else {
                    items(contactHistory, key = { it.id }) { call ->
                        val typeLabel = when (call.type) {
                            CallLog.Calls.INCOMING_TYPE -> "Incoming"
                            CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                            CallLog.Calls.MISSED_TYPE -> "Missed"
                            CallLog.Calls.REJECTED_TYPE -> "Rejected"
                            else -> "Call"
                        }
                        val typeTint = if (call.type == CallLog.Calls.MISSED_TYPE || call.type == CallLog.Calls.REJECTED_TYPE) MoogColors.red else MoogColors.blue
                        val timeLabel = DateUtils.getRelativeTimeSpanString(
                            call.date,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS
                        ).toString()
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MoogColors.surface).padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(typeLabel + " · " + timeLabel, color = typeTint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(call.number + " · " + call.simLabel, color = MoogColors.secondaryLabel, fontSize = 10.sp)
                            }
                            IconButton(onClick = { onMessage(call.number) }) {
                                Icon(Icons.Default.Sms, contentDescription = "Message this number", tint = MoogColors.blue)
                            }
                            IconButton(onClick = { onDial(call.number) }) {
                                Icon(Icons.Default.Call, contentDescription = "Call this number", tint = MoogColors.blue)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class RecentCallGroup(val key: String, val calls: List<RecentCall>)

@Composable
private fun RecentsScreen(
    calls: List<RecentCall>,
    loading: Boolean,
    isDefaultDialer: Boolean,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onDial: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    val filteredCalls = remember(calls, query, filter) {
        calls.filter { call ->
            val matchesFilter = when (filter) {
                "Missed" -> call.type == CallLog.Calls.MISSED_TYPE
                "Incoming" -> call.type == CallLog.Calls.INCOMING_TYPE
                "Outgoing" -> call.type == CallLog.Calls.OUTGOING_TYPE
                else -> true
            }
            val needle = query.trim()
            val matchesQuery = needle.isBlank() ||
                call.displayName.contains(needle, ignoreCase = true) ||
                call.number.contains(needle, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }
    // Collapse only adjacent calls from the same contact, preserving the
    // newest-first call-log timeline rather than grouping the entire history.
    val groupedCalls = remember(filteredCalls) {
        val groups = mutableListOf<RecentCallGroup>()
        filteredCalls.forEach { call ->
            val key = call.displayName.trim().takeIf { it.isNotEmpty() }
                ?: call.number.ifBlank { "Unknown number" }
            val previous = groups.lastOrNull()
            if (previous?.key == key) {
                groups[groups.lastIndex] = previous.copy(calls = previous.calls + call)
            } else {
                groups += RecentCallGroup(key, listOf(call))
            }
        }
        groups
    }
    val context = androidx.compose.ui.platform.LocalContext.current

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
            else -> {
                SearchField(query, { query = it }, "Search name or phone number")
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MoogSegmentedControl(
                        options = listOf("All", "Missed"),
                        selectedIndex = if (filter == "Missed") 1 else 0,
                        onSelected = { filter = if (it == 1) "Missed" else "All" },
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        IconButton(onClick = { filterMenuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More call filters", tint = MoogColors.secondaryLabel)
                        }
                        DropdownMenu(
                            expanded = filterMenuExpanded,
                            onDismissRequest = { filterMenuExpanded = false }
                        ) {
                            listOf("Incoming", "Outgoing").forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        filter = option
                                        filterMenuExpanded = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("All calls") },
                                onClick = {
                                    filter = "All"
                                    filterMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (filteredCalls.isEmpty()) {
                    CenterMessage(
                        if (calls.isEmpty()) "No recent calls" else "No matching calls",
                        if (calls.isEmpty()) "Your incoming, outgoing, and missed calls will appear here."
                        else "Try another filter, name, or phone number."
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(groupedCalls, key = { it.calls.first().id }) { group ->
                            val latestCall = group.calls.first()
                            val blockedByUser = remember(latestCall.number) {
                                SpamBlockRepository.isBlocked(context, latestCall.number)
                            }
                            RecentCallRow(
                                call = latestCall,
                                callCount = group.calls.size,
                                isBlocked = blockedByUser,
                                onDial = { onDial(latestCall.number) },
                                onMessage = {
                                    if (latestCall.number.isNotBlank()) {
                                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", latestCall.number, null)))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentCallRow(
    call: RecentCall,
    callCount: Int = 1,
    isBlocked: Boolean,
    onDial: () -> Unit,
    onMessage: () -> Unit
) {
    val callType = when (call.type) {
        CallLog.Calls.INCOMING_TYPE -> "Incoming"
        CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
        CallLog.Calls.MISSED_TYPE -> "Missed"
        CallLog.Calls.REJECTED_TYPE -> "Rejected"
        CallLog.Calls.BLOCKED_TYPE -> "Blocked"
        CallLog.Calls.VOICEMAIL_TYPE -> "Voicemail"
        else -> "Call"
    }
    val missed = call.type == CallLog.Calls.MISSED_TYPE || call.type == CallLog.Calls.REJECTED_TYPE
    val callColor = if (missed) MoogColors.red else MoogColors.blue
    val callIcon = when (call.type) {
        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> Icons.Default.CallMissed
        CallLog.Calls.INCOMING_TYPE -> Icons.Default.CallReceived
        CallLog.Calls.OUTGOING_TYPE -> Icons.Default.CallMade
        else -> Icons.Default.Call
    }
    val relativeTime = DateUtils.getRelativeTimeSpanString(
        call.date,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()
    val name = call.displayName.ifBlank { call.number.ifBlank { "Unknown number" } }
    val displayName = if (callCount > 1) "$name ($callCount)" else name

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MoogColors.surface)
            .clickable(onClick = onDial).padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(MoogColors.fill), contentAlignment = Alignment.Center) {
            Icon(callIcon, contentDescription = callType, tint = callColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(displayName, color = if (missed) MoogColors.red else MoogColors.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("$callType · $relativeTime", color = callColor, fontSize = 12.sp)
            Text(call.number.ifBlank { "Private number" } + " · " + call.simLabel, color = MoogColors.secondaryLabel, fontSize = 11.sp)
            if (isBlocked) {
                Text("On your blocklist · blocked by you", color = MoogColors.red, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        IconButton(onClick = onMessage, enabled = call.number.isNotBlank()) {
            Icon(Icons.Default.Sms, contentDescription = "Message ${call.number}", tint = MoogColors.blue, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDial, enabled = call.number.isNotBlank()) {
            Icon(Icons.Default.Call, contentDescription = "Call ${call.number}", tint = MoogColors.blue, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SimPreferencePicker(
    phoneAccounts: List<PhoneAccountOption>,
    selectedKey: String?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = if (selectedKey == null) "System default / Ask every time"
        else phoneAccounts.firstOrNull { it.key == selectedKey }?.label ?: "System default / Ask every time"

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MoogColors.surface).padding(14.dp)) {
        Text("Preferred SIM for outgoing calls", color = MoogColors.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("Incoming calls always follow the SIM that receives them.", color = MoogColors.secondaryLabel, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedLabel, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                Text("⌄", color = MoogColors.secondaryLabel)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("System default / Ask every time") },
                    onClick = { onSelect(null); expanded = false }
                )
                phoneAccounts.forEach { account ->
                    DropdownMenuItem(
                        text = { Text(account.label) },
                        onClick = { onSelect(account.key); expanded = false }
                    )
                }
            }
        }
        if (phoneAccounts.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("No SIM calling accounts are currently exposed by Android.", color = MoogColors.secondaryLabel, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingsScreen(
    isDefaultDialer: Boolean,
    hasNotificationPermission: Boolean,
    canUseFullScreenIntent: Boolean,
    hasPhoneStatePermission: Boolean,
    phoneAccounts: List<PhoneAccountOption>,
    selectedPhoneAccountKey: String?,
    onSelectPhoneAccount: (String?) -> Unit,
    onRequestPhoneState: () -> Unit,
    onRequestPhoneRole: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestFullScreenIntent: () -> Unit,
    onOpenSpamProtection: () -> Unit,
    onOpenVoicemail: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var appearanceMode by remember(context) { mutableStateOf(MoogAppearancePreferences.get(context)) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ScreenTitle("Settings", "Make calling feel like yours")
        if (!isDefaultDialer) {
            Button(onClick = onRequestPhoneRole, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text("Set as default Phone app")
            }
            Spacer(Modifier.height(10.dp))
        } else {
            Text("MOOG'S DIALER is your default Phone app", color = MoogColors.blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
        }
        if (!hasNotificationPermission) {
            OutlinedButton(onClick = onRequestNotifications, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text("Allow call notifications")
            }
            Spacer(Modifier.height(10.dp))
        }
        if (Build.VERSION.SDK_INT >= 34 && !canUseFullScreenIntent) {
            OutlinedButton(onClick = onRequestFullScreenIntent, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text("Allow full-screen incoming calls")
            }
            Spacer(Modifier.height(10.dp))
        }
        Text("CALLING SIM", color = MoogColors.secondaryLabel, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (!hasPhoneStatePermission) {
            PermissionCard(
                "Choose a calling SIM",
                "Allow phone-state and phone-number access so MOOG'S DIALER can read SIM calling-account details from Android. Your selection stays on this device.",
                "Allow SIM access",
                onRequestPhoneState
            )
        } else {
            SimPreferencePicker(phoneAccounts, selectedPhoneAccountKey, onSelectPhoneAccount)
        }
        if (!isDefaultDialer) {
            Spacer(Modifier.height(6.dp))
            Text("Set MOOG'S DIALER as the default Phone app for this SIM preference to control outgoing calls. Otherwise Android opens the system dialer.", color = MoogColors.secondaryLabel, fontSize = 11.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.height(14.dp))
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenVoicemail),
            colors = CardDefaults.cardColors(containerColor = MoogColors.surface)
        ) {
            Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Voicemail", color = MoogColors.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Visual inbox, playback and per-SIM mailbox numbers", color = MoogColors.secondaryLabel, fontSize = 12.sp)
                }
                Text("Open  ›", color = MoogColors.blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSpamProtection),
            colors = CardDefaults.cardColors(containerColor = MoogColors.surface)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Spam protection", color = MoogColors.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Manage your local blocklist and blocked callers", color = MoogColors.secondaryLabel, fontSize = 12.sp)
                }
                Text("Manage  ›", color = MoogColors.blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(6.dp))
        MoogGroupedSection(title = "APPEARANCE", footer = "System follows your phone's display setting.") {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                MoogSegmentedControl(
                    options = listOf("System", "Light", "Dark"),
                    selectedIndex = appearanceMode.ordinal,
                    onSelected = { index ->
                        appearanceMode = MoogAppearanceMode.values()[index]
                        MoogAppearancePreferences.set(context, appearanceMode)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        SettingsRow("Contacts", "On-device contact access")
        SettingsRow("Calling", "Telecom calling with system dialer fallback")
        SettingsRow("Privacy", "No account or cloud contact sync")
        SettingsRow("About", "MOOG'S DIALER · Version 0.2.0")
        Spacer(Modifier.height(18.dp))
        Text("BUILT FOR YOUR EVERYDAY CONNECTIONS", color = MoogColors.blue, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Call controls are provided by Android Telecom. Incoming-call notifications require notification access; full-screen presentation also depends on Android's notification and full-screen-intent settings.", color = MoogColors.secondaryLabel, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun ScreenTitle(title: String, subtitle: String) {
    Spacer(Modifier.height(14.dp))
    Text(title, color = MoogColors.label, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.9).sp)
    Spacer(Modifier.height(3.dp))
    Text(subtitle, color = MoogColors.secondaryLabel, fontSize = 13.sp)
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MoogColors.fill).padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = MoogColors.secondaryLabel, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(color = MoogColors.label, fontSize = 14.sp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, color = MoogColors.secondaryLabel, fontSize = 14.sp)
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MoogColors.surface).clickable(onClick = onOpenContact).padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(contact.displayName)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(contact.displayName, color = MoogColors.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(contact.phoneNumber, color = MoogColors.secondaryLabel, fontSize = 12.sp)
        }
        if (showFavorite) {
            IconButton(onClick = onFavorite) {
                Icon(Icons.Default.Favorite, contentDescription = if (favorite) "Remove favorite" else "Add favorite",
                    tint = if (favorite) MoogColors.blue else MoogColors.secondaryLabel.copy(alpha = 0.6f), modifier = Modifier.size(19.dp))
            }
        }
        IconButton(onClick = onDial) {
            Icon(Icons.Default.Call, contentDescription = "Call ${contact.displayName}", tint = MoogColors.blue, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun Avatar(name: String) {
    val initials = name.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("").uppercase()
    Box(Modifier.size(42.dp).clip(CircleShape).background(MoogColors.fill), contentAlignment = Alignment.Center) {
        Text(initials.ifBlank { "?" }, color = MoogColors.blue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PermissionCard(title: String, body: String, button: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MoogColors.surface).padding(20.dp)) {
        Icon(Icons.Default.Contacts, contentDescription = null, tint = MoogColors.blue, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = MoogColors.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = MoogColors.secondaryLabel, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onClick, shape = RoundedCornerShape(12.dp)) { Text(button) }
    }
}

@Composable
private fun CenterMessage(title: String, body: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(horizontal = 30.dp).offset(y = (-24).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(62.dp).clip(CircleShape).background(MoogColors.fill), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, contentDescription = null, tint = MoogColors.blue, modifier = Modifier.size(27.dp))
            }
            Spacer(Modifier.height(15.dp))
            Text(title, color = MoogColors.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(body, color = MoogColors.secondaryLabel, fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SettingsRow(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 15.dp)) {
        Text(title, color = MoogColors.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(detail, color = MoogColors.secondaryLabel, fontSize = 12.sp)
    }
    HorizontalDivider(color = MoogColors.separator, thickness = 0.8.dp)
}

@Composable
private fun BottomTabs(selected: Tab, onSelect: (Tab) -> Unit) {
    val items = listOf(
        MoogTabItem(Tab.Favorites.name, Tab.Favorites.label, Icons.Default.Favorite),
        MoogTabItem(Tab.Recents.name, Tab.Recents.label, Icons.Default.History),
        MoogTabItem(Tab.Contacts.name, Tab.Contacts.label, Icons.Default.Person),
        MoogTabItem(Tab.Keypad.name, Tab.Keypad.label, Icons.Default.Dialpad),
        MoogTabItem(Tab.Settings.name, Tab.Settings.label, Icons.Default.Settings)
    )
    MoogTabBar(
        items = items,
        selectedKey = selected.name,
        onSelect = { key -> Tab.values().firstOrNull { it.name == key }?.let(onSelect) }
    )
}
