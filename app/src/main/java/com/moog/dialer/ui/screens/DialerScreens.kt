package com.moog.dialer.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.produceState
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.util.LruCache
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
    var selectedRecentCall by remember { mutableStateOf<RecentCall?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    BackHandler(enabled = selectedContactId != null || selectedRecentCall != null) {
        if (selectedRecentCall != null) selectedRecentCall = null else selectedContactId = null
    }
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
        if (selectedRecentCall != null) {
            Box(Modifier.weight(1f)) {
                CallDetailScreen(
                    call = selectedRecentCall!!,
                    onBack = { selectedRecentCall = null },
                    onDial = onDial,
                    onMessage = { phoneNumber ->
                        if (phoneNumber.isNotBlank()) {
                            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", phoneNumber, null)))
                        }
                    }
                )
            }
        } else AnimatedContent(
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
                Tab.Favorites -> if (selectedContactId != null) {
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
                } else FavoritesScreen(
                    contacts = contacts.filter { favorites.contains(it.id) },
                    hasPermission = hasContactsPermission,
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                    onOpenContact = { selectedContactId = it },
                    onDial = onDial
                )
                Tab.Recents -> if (selectedRecentCall != null) {
                    CallDetailScreen(
                        call = selectedRecentCall!!,
                        onBack = { selectedRecentCall = null },
                        onDial = onDial,
                        onMessage = { phoneNumber ->
                            if (phoneNumber.isNotBlank()) {
                                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", phoneNumber, null)))
                            }
                        }
                    )
                } else RecentsScreen(
                    calls = recentCalls,
                    loading = loadingRecentCalls,
                    isDefaultDialer = isDefaultDialer,
                    hasPermission = hasCallLogPermission,
                    onRequestPermission = { callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG) },
                    onDial = onDial,
                    onOpenDetails = { selectedRecentCall = it }
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
        if (selectedContactId == null && selectedRecentCall == null) {
            BottomTabs(selected = selectedTab, onSelect = {
                selectedTab = it
                selectedContactId = null
                selectedRecentCall = null
                search = ""
            })
        }
    }
}

@Composable
internal fun ScreenTitle(title: String, subtitle: String) {
    Spacer(Modifier.height(14.dp))
    Text(title, color = MoogColors.label, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.9).sp)
    Spacer(Modifier.height(3.dp))
    Text(subtitle, color = MoogColors.secondaryLabel, fontSize = 13.sp)
    Spacer(Modifier.height(20.dp))
}

@Composable
internal fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
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
internal fun ContactRow(
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
        Avatar(contact.displayName, contact.photoUri)
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

private object ContactPhotoCache {
    private val cache = LruCache<String, Bitmap>(48)

    fun get(uri: String): Bitmap? = cache.get(uri)

    fun put(uri: String, bitmap: Bitmap) {
        cache.put(uri, bitmap)
    }
}

@Composable
internal fun Avatar(name: String, photoUri: String? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val initials = name.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("").uppercase()
    val photo by produceState<Bitmap?>(initialValue = null, photoUri) {
        value = withContext(Dispatchers.IO) {
            photoUri?.let { uri ->
                ContactPhotoCache.get(uri) ?: runCatching {
                    context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream -> BitmapFactory.decodeStream(stream) }
                }.getOrNull()?.also { ContactPhotoCache.put(uri, it) }
            }
        }
    }
    Box(Modifier.size(42.dp).clip(CircleShape).background(MoogColors.fill), contentAlignment = Alignment.Center) {
        if (photo != null) {
            Image(
                bitmap = photo!!.asImageBitmap(),
                contentDescription = "$name contact photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(initials.ifBlank { "?" }, color = MoogColors.blue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun PermissionCard(title: String, body: String, button: String, onClick: () -> Unit) {
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
internal fun CenterMessage(title: String, body: String) {
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
internal fun SettingsRow(title: String, detail: String) {
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
