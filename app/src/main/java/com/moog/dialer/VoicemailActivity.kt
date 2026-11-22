package com.moog.dialer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.moog.dialer.data.PhoneAccountOption
import com.moog.dialer.data.PhoneAccountRepository
import com.moog.dialer.data.VoicemailLoadResult
import com.moog.dialer.data.VoicemailMessage
import com.moog.dialer.data.VoicemailRepository
import com.moog.dialer.ui.components.MoogConfirmSheet
import com.moog.dialer.ui.components.MoogGroupedSection
import com.moog.dialer.ui.components.MoogLargeTitleScaffold
import com.moog.dialer.ui.components.MoogListRow
import com.moog.dialer.ui.theme.MoogColors
import com.moog.dialer.ui.theme.MoogTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VoicemailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MoogTheme {
                VoicemailScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun VoicemailScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(VoicemailRepository.hasVoicemailPermission(context)) }
    var loadResult by remember { mutableStateOf<VoicemailLoadResult?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var isDefaultDialer by remember { mutableStateOf(isMoogDefaultPhoneApp(context)) }
    var phoneAccounts by remember { mutableStateOf<List<PhoneAccountOption>>(emptyList()) }
    var hasNotificationPermission by remember { mutableStateOf(android.os.Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    var phoneNumberPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED)
    }
    var defaultNumber by remember { mutableStateOf(VoicemailRepository.getNumber(context).ifBlank { VoicemailRepository.resolveDeviceVoicemailNumber(context).orEmpty() }) }
    var perSimNumbers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showSettings by remember { mutableStateOf(false) }
    var expandedMessageId by remember { mutableStateOf<Long?>(null) }
    var pendingDelete by remember { mutableStateOf<VoicemailMessage?>(null) }
    val player = remember { VoicemailPlaybackController(context) }

    val voicemailPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) refreshKey++
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasNotificationPermission = granted }
    val phonePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        phoneNumberPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
        if (phoneNumberPermission) phoneAccounts = PhoneAccountRepository.getCallAccounts(context)
    }

    LaunchedEffect(hasPermission, refreshKey) {
        loadResult = if (hasPermission) withContext(Dispatchers.IO) { VoicemailRepository.load(context) }
        else VoicemailLoadResult.PermissionRequired
    }
    LaunchedEffect(phoneNumberPermission) {
        if (phoneNumberPermission) {
            phoneAccounts = PhoneAccountRepository.getCallAccounts(context)
            perSimNumbers = phoneAccounts.associate { it.key to VoicemailRepository.getNumber(context, it.key) }
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    if (showSettings) {
        VoicemailSettingsScreen(
            defaultNumber = defaultNumber,
            onDefaultNumberChange = { defaultNumber = it },
            phoneNumberPermission = phoneNumberPermission,
            phoneAccounts = phoneAccounts,
            perSimNumbers = perSimNumbers,
            onPerSimNumberChange = { key, value -> perSimNumbers = perSimNumbers + (key to value) },
            onSaveDefault = { VoicemailRepository.saveNumber(context, null, defaultNumber) },
            onSaveSim = { key, value -> VoicemailRepository.saveNumber(context, key, value) },
            onCallDefault = { openVoicemailCall(context, defaultNumber, null) },
            onCallSim = { value, account -> openVoicemailCall(context, value, account) },
            onRequestSimPermission = { phonePermissionLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS)) },
            onBack = { showSettings = false }
        )
        return
    }

    val messages = (loadResult as? VoicemailLoadResult.Available)?.messages.orEmpty()
    val unreadCount = messages.count { !it.isRead }

    MoogLargeTitleScaffold(
        title = stringResource(R.string.voicemail),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = MoogColors.blue)
            }
        },
        actions = {
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.voicemail_settings), tint = MoogColors.blue)
            }
            IconButton(onClick = { VoicemailRepository.requestSync(context); refreshKey++ }) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh_voicemail), tint = MoogColors.blue)
            }
        }
    ) { scaffoldPadding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(scaffoldPadding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    if (unreadCount == 0) stringResource(R.string.your_voicemail_inbox)
                    else pluralStringResource(R.plurals.unread_voicemail_count, unreadCount, unreadCount),
                    color = MoogColors.secondaryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MoogColors.surface).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.carrier_visual_voicemail), color = MoogColors.blue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text(
                        when {
                            !isDefaultDialer -> stringResource(R.string.set_default_phone_app)
                            loadResult is VoicemailLoadResult.Available && messages.isNotEmpty() -> stringResource(R.string.carrier_messages_available)
                            loadResult is VoicemailLoadResult.Available -> stringResource(R.string.no_voicemail_messages)
                            loadResult is VoicemailLoadResult.ProviderUnavailable -> stringResource(R.string.voicemail_provider_unavailable)
                            else -> stringResource(R.string.connect_to_carrier_voicemail)
                        },
                        color = MoogColors.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(stringResource(R.string.voicemail_source_explanation), color = MoogColors.secondaryLabel, fontSize = 11.sp, lineHeight = 16.sp)
                    when (loadResult) {
                        VoicemailLoadResult.PermissionRequired -> MoogActionRow(stringResource(R.string.allow_voicemail_access)) {
                            voicemailPermissionLauncher.launch(Manifest.permission.ADD_VOICEMAIL)
                        }
                        VoicemailLoadResult.ProviderUnavailable -> MoogActionRow(stringResource(R.string.try_again)) { refreshKey++ }
                        else -> Unit
                    }
                    if (!hasNotificationPermission && android.os.Build.VERSION.SDK_INT >= 33) {
                        MoogActionRow(stringResource(R.string.enable_voicemail_notifications)) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    if (!isDefaultDialer) Text(stringResource(R.string.default_phone_visual_voicemail_note), color = MoogColors.secondaryLabel, fontSize = 10.sp)
                }
            }

            if (messages.isEmpty() && loadResult is VoicemailLoadResult.Available) {
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MoogColors.surface).padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(R.string.inbox_clear), color = MoogColors.label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(stringResource(R.string.inbox_sync_hint), color = MoogColors.secondaryLabel, fontSize = 11.sp)
                    }
                }
            }

            items(messages, key = { it.id }) { message ->
                val callerName = remember(message.number, hasContactsPermission(context)) {
                    resolveVoicemailCaller(context, message.number)
                }
                VoicemailMessageRow(
                    message = message,
                    callerName = callerName,
                    expanded = expandedMessageId == message.id,
                    player = player,
                    onExpand = { expandedMessageId = if (expandedMessageId == message.id) null else message.id },
                    onPlay = {
                        if (!message.isRead) VoicemailRepository.markRead(context, message, true)
                        player.toggle(message)
                        refreshKey++
                    },
                    onToggleRead = {
                        VoicemailRepository.markRead(context, message, !message.isRead)
                        refreshKey++
                    },
                    onRequestDelete = { pendingDelete = message },
                    onCallback = { openVoicemailCall(context, message.number, null) }
                )
            }
        }
    }

    pendingDelete?.let { message ->
        MoogConfirmSheet(
            title = stringResource(R.string.delete_voicemail_title),
            message = stringResource(R.string.delete_voicemail_confirmation),
            confirmLabel = stringResource(R.string.delete_voicemail),
            onConfirm = {
                if (player.currentMessageId == message.id) player.stop()
                VoicemailRepository.delete(context, message)
                pendingDelete = null
                refreshKey++
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun VoicemailMessageRow(
    message: VoicemailMessage,
    callerName: String,
    expanded: Boolean,
    player: VoicemailPlaybackController,
    onExpand: () -> Unit,
    onPlay: () -> Unit,
    onToggleRead: () -> Unit,
    onRequestDelete: () -> Unit,
    onCallback: () -> Unit
) {
    val active = player.currentMessageId == message.id
    val relativeDate = DateUtils.getRelativeTimeSpanString(
        message.dateMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
    ).toString()
    var menuExpanded by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onToggleRead()
                SwipeToDismissBoxValue.EndToStart -> onRequestDelete()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val deleteSide = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Row(
                Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                    .background(if (deleteSide) MoogColors.red.copy(alpha = 0.12f) else MoogColors.blue.copy(alpha = 0.12f))
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (deleteSide) Arrangement.End else Arrangement.Start
            ) {
                Text(
                    if (deleteSide) stringResource(R.string.delete_voicemail) else stringResource(if (message.isRead) R.string.mark_unread else R.string.mark_read),
                    color = if (deleteSide) MoogColors.red else MoogColors.blue,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        content = {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MoogColors.surface)
                    .combinedClickable(onClick = onExpand, onLongClick = { menuExpanded = true })
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(9.dp).clip(CircleShape)
                            .background(if (message.isRead) Color.Transparent else MoogColors.blue)
                            .semantics { contentDescription = if (message.isRead) "" else "Unread voicemail" }
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(callerName, color = MoogColors.label, fontSize = 15.sp, fontWeight = if (message.isRead) FontWeight.Medium else FontWeight.Bold)
                        Text(message.number.ifBlank { stringResource(R.string.unknown_caller) }, color = MoogColors.secondaryLabel, fontSize = 11.sp)
                    }
                    Text(relativeDate, color = MoogColors.secondaryLabel, fontSize = 10.sp)
                }
                Row(Modifier.padding(start = 21.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(formatDuration(message.durationSeconds), color = MoogColors.secondaryLabel, fontSize = 10.sp)
                    Text("·", color = MoogColors.secondaryLabel, fontSize = 10.sp)
                    Text(message.accountLabel, color = MoogColors.secondaryLabel, fontSize = 10.sp)
                }

                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (message.isRead) R.string.mark_unread else R.string.mark_read)) },
                        onClick = { menuExpanded = false; onToggleRead() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_voicemail), color = MoogColors.red) },
                        onClick = { menuExpanded = false; onRequestDelete() }
                    )
                }

                if (expanded) {
                    HorizontalDivider(color = MoogColors.separator)
                    val totalDurationMs = if (active) player.durationMs else message.durationSeconds * 1000L
                    val elapsedMs = if (active) player.positionMs else 0L
                    Slider(
                        value = if (active) player.progress else 0f,
                        onValueChange = { if (active) player.seekTo(it) },
                        valueRange = 0f..1f,
                        modifier = Modifier.semantics { contentDescription = "Voicemail playback position" }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatDuration(elapsedMs / 1000), color = MoogColors.secondaryLabel, fontSize = 10.sp)
                        Spacer(Modifier.weight(1f))
                        listOf(1f, 1.5f, 2f).forEach { speed ->
                            Surface(
                                onClick = { player.setSpeed(speed) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (player.playbackSpeed == speed) MoogColors.blue.copy(alpha = 0.12f) else Color.Transparent
                            ) {
                                Text(
                                    stringResource(R.string.playback_speed, speed.toString()),
                                    color = if (player.playbackSpeed == speed) MoogColors.blue else MoogColors.secondaryLabel,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text("−${formatDuration((totalDurationMs - elapsedMs).coerceAtLeast(0L) / 1000)}", color = MoogColors.secondaryLabel, fontSize = 10.sp)
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MoogActionRow(
                            label = stringResource(if (active && player.isPlaying) R.string.pause else R.string.play),
                            enabled = message.hasContent,
                            modifier = Modifier.weight(1f),
                            leading = {
                                Icon(if (active && player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = MoogColors.blue)
                            },
                            onClick = onPlay
                        )
                        MoogActionRow(
                            label = stringResource(R.string.call_back),
                            enabled = message.number.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            leading = { Icon(Icons.Default.Call, contentDescription = null, tint = MoogColors.blue) },
                            onClick = onCallback
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MoogActionRow(
                            label = stringResource(if (message.isRead) R.string.mark_unread else R.string.mark_read),
                            modifier = Modifier.weight(1f),
                            onClick = onToggleRead
                        )
                        MoogActionRow(
                            label = stringResource(R.string.delete_voicemail),
                            destructive = true,
                            modifier = Modifier.weight(1f),
                            leading = { Icon(Icons.Default.Delete, contentDescription = null, tint = MoogColors.red) },
                            onClick = onRequestDelete
                        )
                    }
                    if (!message.hasContent) {
                        Text(stringResource(R.string.voicemail_audio_not_available), color = MoogColors.secondaryLabel, fontSize = 10.sp)
                    }
                }
            }
        }
    )
}

@Composable
private fun VoicemailSettingsScreen(
    defaultNumber: String,
    onDefaultNumberChange: (String) -> Unit,
    phoneNumberPermission: Boolean,
    phoneAccounts: List<PhoneAccountOption>,
    perSimNumbers: Map<String, String>,
    onPerSimNumberChange: (String, String) -> Unit,
    onSaveDefault: () -> Unit,
    onSaveSim: (String, String) -> Unit,
    onCallDefault: () -> Unit,
    onCallSim: (String, PhoneAccountOption) -> Unit,
    onRequestSimPermission: () -> Unit,
    onBack: () -> Unit
) {
    MoogLargeTitleScaffold(
        title = stringResource(R.string.voicemail_settings),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = MoogColors.blue)
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                MoogGroupedSection(
                    title = stringResource(R.string.carrier_mailbox_fallback),
                    footer = stringResource(R.string.save_voicemail_number_hint)
                ) {
                    VoicemailSettingField(
                        label = stringResource(R.string.default_voicemail_number),
                        value = defaultNumber,
                        onValueChange = onDefaultNumberChange
                    )
                    HorizontalDivider(color = MoogColors.separator)
                    MoogListRow(
                        title = stringResource(R.string.save_voicemail_number),
                        onClick = onSaveDefault,
                        destructive = false
                    )
                    HorizontalDivider(color = MoogColors.separator)
                    MoogListRow(
                        title = stringResource(R.string.call_voicemail),
                        leading = { Icon(Icons.Default.Call, contentDescription = null, tint = MoogColors.blue) },
                        onClick = onCallDefault
                    )
                }
            }
            item {
                MoogGroupedSection(
                    title = stringResource(R.string.per_sim_mailbox_numbers),
                    footer = stringResource(R.string.voicemail_sim_numbers_explanation)
                ) {
                    if (!phoneNumberPermission) {
                        MoogListRow(
                            title = stringResource(R.string.allow_sim_access_mailbox),
                            onClick = onRequestSimPermission,
                            showChevron = true
                        )
                    } else if (phoneAccounts.isEmpty()) {
                        MoogListRow(title = stringResource(R.string.no_sim_accounts_available))
                    } else {
                        phoneAccounts.forEachIndexed { index, account ->
                            val value = perSimNumbers[account.key].orEmpty()
                            VoicemailSettingField(
                                label = account.label,
                                value = value,
                                onValueChange = { onPerSimNumberChange(account.key, it) }
                            )
                            HorizontalDivider(color = MoogColors.separator)
                            MoogListRow(
                                title = stringResource(R.string.save_for_sim, account.label),
                                onClick = { onSaveSim(account.key, value) }
                            )
                            MoogListRow(
                                title = stringResource(R.string.call_using_sim, account.label),
                                leading = { Icon(Icons.Default.Call, contentDescription = null, tint = MoogColors.blue) },
                                onClick = { onCallSim(value, account) }
                            )
                            if (index != phoneAccounts.lastIndex) HorizontalDivider(color = MoogColors.separator)
                        }
                    }
                }
            }
            item {
                MoogGroupedSection(
                    title = stringResource(R.string.custom_voicemail_server),
                    footer = stringResource(R.string.custom_voicemail_server_explanation)
                ) {
                    MoogListRow(
                        title = stringResource(R.string.custom_voicemail_not_connected),
                        subtitle = stringResource(R.string.custom_voicemail_server_honesty)
                    )
                }
            }
        }
    }
}

@Composable
private fun VoicemailSettingField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, color = MoogColors.secondaryLabel, style = MaterialTheme.typography.labelMedium)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MoogColors.label),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(stringResource(R.string.enter_voicemail_number), color = MoogColors.secondaryLabel, style = MaterialTheme.typography.bodyLarge)
                inner()
            }
        )
    }
}

@Composable
private fun MoogActionRow(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.defaultMinSize(minHeight = 42.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (destructive) MoogColors.red.copy(alpha = 0.09f) else MoogColors.fill,
        enabled = enabled,
        onClick = onClick
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading?.invoke()
            if (leading != null) Spacer(Modifier.width(6.dp))
            Text(label, color = if (destructive) MoogColors.red else if (enabled) MoogColors.blue else MoogColors.secondaryLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun hasContactsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

private fun resolveVoicemailCaller(context: Context, number: String): String {
    if (number.isBlank() || !hasContactsPermission(context)) return number.ifBlank { context.getString(R.string.unknown_caller) }
    val lookupUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
    return runCatching {
        context.contentResolver.query(
            lookupUri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor: Cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
        }
    }.getOrNull() ?: number
}

private class VoicemailPlaybackController(private val context: Context) {
    var currentMessageId by mutableStateOf<Long?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var playbackSpeed by mutableFloatStateOf(1f)
        private set

    private var mediaPlayer: MediaPlayer? = null
    private var prepared = false
    private val handler = Handler(Looper.getMainLooper())
    private val progressTask = object : Runnable {
        override fun run() {
            val activePlayer = mediaPlayer ?: return
            if (activePlayer.isPlaying) {
                positionMs = activePlayer.currentPosition.toLong()
                durationMs = activePlayer.duration.toLong().coerceAtLeast(0L)
                progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                handler.postDelayed(this, 250L)
            }
        }
    }

    fun toggle(message: VoicemailMessage) {
        val current = mediaPlayer
        if (currentMessageId == message.id && current != null) {
            if (!prepared) return
            if (current.isPlaying) {
                current.pause()
                isPlaying = false
                handler.removeCallbacks(progressTask)
            } else {
                if (durationMs > 0L && positionMs >= durationMs) {
                    current.seekTo(0)
                    positionMs = 0L
                    progress = 0f
                }
                current.start()
                isPlaying = true
                handler.post(progressTask)
            }
            return
        }
        stop()
        currentMessageId = message.id
        val next = MediaPlayer()
        mediaPlayer = next
        next.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        next.setOnPreparedListener {
            prepared = true
            durationMs = it.duration.toLong().coerceAtLeast(0L)
            it.start()
            isPlaying = true
            handler.post(progressTask)
        }
        next.setOnCompletionListener {
            isPlaying = false
            positionMs = durationMs
            progress = 1f
            handler.removeCallbacks(progressTask)
        }
        next.setOnErrorListener { _, _, _ -> stop(); true }
        runCatching {
            next.setDataSource(context, message.contentUri)
            next.prepareAsync()
        }.onFailure { stop() }
    }

    fun seekTo(value: Float) {
        if (durationMs <= 0L) return
        val position = (durationMs * value.coerceIn(0f, 1f)).toInt()
        runCatching { mediaPlayer?.seekTo(position) }
        positionMs = position.toLong()
        progress = value.coerceIn(0f, 1f)
    }

    fun setSpeed(value: Float) {
        playbackSpeed = value
        runCatching {
            val params = mediaPlayer?.playbackParams ?: return
            mediaPlayer?.playbackParams = params.setSpeed(value)
        }
    }

    fun stop() {
        handler.removeCallbacks(progressTask)
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        prepared = false
        currentMessageId = null
        isPlaying = false
        progress = 0f
        positionMs = 0L
        durationMs = 0L
        playbackSpeed = 1f
    }

    fun release() = stop()
}

private fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    return "%d:%02d".format(safe / 60, safe % 60)
}

private fun openVoicemailCall(context: Context, number: String, account: PhoneAccountOption?) {
    if (number.isBlank()) return
    val uri = Uri.fromParts("tel", number.trim(), null)
    if (isMoogDefaultPhoneApp(context) && account != null) {
        runCatching {
            val extras = Bundle().apply { putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account.handle) }
            context.getSystemService(TelecomManager::class.java).placeCall(uri, extras)
        }.onFailure {
            context.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    } else {
        context.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
