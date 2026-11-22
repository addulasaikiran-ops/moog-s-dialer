package com.moog.dialer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.moog.dialer.data.PhoneAccountOption
import com.moog.dialer.data.PhoneAccountRepository
import com.moog.dialer.data.VoicemailLoadResult
import com.moog.dialer.data.VoicemailMessage
import com.moog.dialer.data.VoicemailRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val VmPage = Color(0xFFF5F6F8)
private val VmCard = Color.White
private val VmInk = Color(0xFF17191D)
private val VmMuted = Color(0xFF858B96)
private val VmBlue = Color(0xFF087CFA)

class VoicemailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = VmBlue, background = VmPage, surface = VmCard)) {
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
    var isDefaultDialer by remember { mutableStateOf(isMoogDefaultDialer(context)) }
    var phoneAccounts by remember { mutableStateOf<List<PhoneAccountOption>>(emptyList()) }
    var phoneNumberPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED)
    }
    var defaultNumber by remember { mutableStateOf(VoicemailRepository.getNumber(context).ifBlank { VoicemailRepository.resolveDeviceVoicemailNumber(context).orEmpty() }) }
    var perSimNumbers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val player = remember { VoicemailPlaybackController(context) }

    val voicemailPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) refreshKey++
    }
    val phonePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        phoneNumberPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
        if (phoneNumberPermission) phoneAccounts = PhoneAccountRepository.getCallAccounts(context)
    }

    LaunchedEffect(hasPermission, refreshKey) {
        loadResult = if (hasPermission) withContext(Dispatchers.IO) {
            VoicemailRepository.load(context)
        } else VoicemailLoadResult.PermissionRequired
    }
    LaunchedEffect(phoneNumberPermission) {
        if (phoneNumberPermission) {
            phoneAccounts = PhoneAccountRepository.getCallAccounts(context)
            perSimNumbers = phoneAccounts.associate { it.key to VoicemailRepository.getNumber(context, it.key) }
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    val messages = (loadResult as? VoicemailLoadResult.Available)?.messages.orEmpty()
    val unreadCount = messages.count { !it.isRead }

    Column(Modifier.fillMaxSize().background(VmPage)) {
        Row(
            Modifier.fillMaxWidth().background(VmCard).statusBarsPadding().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = VmInk) }
            Column(Modifier.weight(1f)) {
                Text("Voicemail", color = VmInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(if (unreadCount == 0) "Your voicemail inbox" else unreadCount.toString() + " unread messages", color = VmMuted, fontSize = 11.sp)
            }
            IconButton(onClick = {
                VoicemailRepository.requestSync(context)
                refreshKey++
            }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh voicemail", tint = VmBlue) }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(VmCard).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("CARRIER VISUAL VOICEMAIL", color = VmBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text(
                        when {
                            !isDefaultDialer -> "Set MOOG'S DIALER as your default Phone app"
                            loadResult is VoicemailLoadResult.Available && messages.isNotEmpty() -> "Carrier voicemail messages are available"
                            loadResult is VoicemailLoadResult.Available -> "No voicemail messages were returned by the device"
                            loadResult is VoicemailLoadResult.ProviderUnavailable -> "Voicemail provider is unavailable"
                            else -> "Connect to your carrier voicemail"
                        },
                        color = VmInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Messages appear here only when a carrier or voicemail source provisions them through Android. MOOG does not record missed calls or create carrier messages.",
                        color = VmMuted, fontSize = 11.sp, lineHeight = 16.sp
                    )
                    when (loadResult) {
                        VoicemailLoadResult.PermissionRequired -> Button(
                            onClick = { voicemailPermissionLauncher.launch(Manifest.permission.ADD_VOICEMAIL) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Allow voicemail access") }
                        VoicemailLoadResult.ProviderUnavailable -> OutlinedButton(
                            onClick = { refreshKey++ }, modifier = Modifier.fillMaxWidth()
                        ) { Text("Try again") }
                        else -> Unit
                    }
                    if (!isDefaultDialer) {
                        Text("Android delivers visual voicemail events only to the default Phone app.", color = VmMuted, fontSize = 10.sp)
                    }
                }
            }

            if (messages.isEmpty() && loadResult is VoicemailLoadResult.Available) {
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(VmCard).padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Your inbox is clear", color = VmInk, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("When a supported voicemail source syncs a message, it will show up here.", color = VmMuted, fontSize = 11.sp)
                    }
                }
            }

            items(messages, key = { it.id }) { message ->
                VoicemailMessageCard(
                    message = message,
                    player = player,
                    onPlay = {
                        if (!message.isRead) VoicemailRepository.markRead(context, message, true)
                        player.toggle(message)
                        refreshKey++
                    },
                    onToggleRead = {
                        VoicemailRepository.markRead(context, message, !message.isRead)
                        refreshKey++
                    },
                    onDelete = {
                        if (player.currentMessageId == message.id) player.stop()
                        VoicemailRepository.delete(context, message)
                        refreshKey++
                    },
                    onCallback = { openVoicemailCall(context, message.number, null) }
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(VmCard).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("CALL YOUR VOICEMAIL", color = VmBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text("Carrier mailbox fallback", color = VmInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Save the voicemail access number supplied by your carrier. MOOG does not guess a carrier code.", color = VmMuted, fontSize = 11.sp, lineHeight = 16.sp)
                    OutlinedTextField(value = defaultNumber, onValueChange = { defaultNumber = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Default voicemail number") }, singleLine = true)
                    Button(onClick = { VoicemailRepository.saveNumber(context, null, defaultNumber) }, modifier = Modifier.fillMaxWidth(), enabled = defaultNumber.isNotBlank()) {
                        Text("Save voicemail number")
                    }
                    OutlinedButton(onClick = { if (defaultNumber.isNotBlank()) openVoicemailCall(context, defaultNumber, null) }, modifier = Modifier.fillMaxWidth(), enabled = defaultNumber.isNotBlank()) {
                        Icon(Icons.Default.Call, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Call voicemail")
                    }
                    if (!phoneNumberPermission) {
                        TextButton(onClick = { phonePermissionLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS)) }) {
                            Text("Allow SIM access to configure separate mailbox numbers")
                        }
                    } else if (phoneAccounts.isNotEmpty()) {
                        HorizontalDivider()
                        Text("PER-SIM MAILBOX NUMBERS", color = VmMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                        phoneAccounts.forEach { account ->
                            val value = perSimNumbers[account.key].orEmpty()
                            OutlinedTextField(value = value, onValueChange = { perSimNumbers = perSimNumbers + (account.key to it) }, modifier = Modifier.fillMaxWidth(), label = { Text(account.label) }, singleLine = true)
                            OutlinedButton(onClick = { VoicemailRepository.saveNumber(context, account.key, value) }, modifier = Modifier.fillMaxWidth(), enabled = value.isNotBlank()) {
                                Text("Save for " + account.label)
                            }
                            OutlinedButton(onClick = { openVoicemailCall(context, value, account) }, modifier = Modifier.fillMaxWidth(), enabled = value.isNotBlank()) {
                                Text("Call using " + account.label)
                            }
                        }
                    }
                }
            }

            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(VmCard).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("CUSTOM VOICEMAIL SERVER", color = VmBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text("Integration point prepared · server not connected", color = VmInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "A private voicemail server needs a real endpoint, authentication contract, secure media storage, and carrier call-forwarding setup. No endpoint or credentials are configured, so this build does not pretend to host or retrieve custom-server messages.",
                        color = VmMuted, fontSize = 11.sp, lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun VoicemailMessageCard(
    message: VoicemailMessage,
    player: VoicemailPlaybackController,
    onPlay: () -> Unit,
    onToggleRead: () -> Unit,
    onDelete: () -> Unit,
    onCallback: () -> Unit
) {
    val active = player.currentMessageId == message.id
    val time = DateUtils.getRelativeTimeSpanString(message.dateMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(VmCard).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFEAF3FF)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = VmBlue)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(message.number.ifBlank { "Unknown caller" }, color = VmInk, fontSize = 14.sp, fontWeight = if (message.isRead) FontWeight.Medium else FontWeight.Bold)
                Text(time + " · " + formatDuration(message.durationSeconds) + " · " + message.accountLabel, color = VmMuted, fontSize = 10.sp)
            }
            if (!message.isRead) Box(Modifier.size(7.dp).clip(CircleShape).background(VmBlue))
        }
        if (active) {
            Slider(value = player.progress, onValueChange = { player.seekTo(it) }, valueRange = 0f..1f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(player.positionMs / 1000), color = VmMuted, fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                listOf(1f, 1.5f, 2f).forEach { speed ->
                    TextButton(onClick = { player.setSpeed(speed) }, contentPadding = PaddingValues(horizontal = 5.dp)) {
                        Text(speed.toString() + "x", color = if (player.speed == speed) VmBlue else VmMuted, fontSize = 10.sp)
                    }
                }
                Text(formatDuration(player.durationMs / 1000), color = VmMuted, fontSize = 10.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onPlay, enabled = message.hasContent, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                Icon(if (active && player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text(if (!message.hasContent) "Audio unavailable" else if (active && player.isPlaying) "Pause" else "Play")
            }
            TextButton(onClick = onCallback, enabled = message.number.isNotBlank()) { Text("Callback") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onToggleRead) { Text(if (message.isRead) "Mark unread" else "Mark read") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete voicemail", tint = VmMuted) }
        }
        if (!message.hasContent) Text("The voicemail source has not made the audio content available yet.", color = VmMuted, fontSize = 10.sp)
    }
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
    var speed by mutableFloatStateOf(1f)
        private set

    private var mediaPlayer: MediaPlayer? = null
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
            if (current.isPlaying) {
                current.pause()
                isPlaying = false
                handler.removeCallbacks(progressTask)
            } else {
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
        speed = value
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
        currentMessageId = null
        isPlaying = false
        progress = 0f
        positionMs = 0L
        durationMs = 0L
        speed = 1f
    }

    fun release() = stop()
}

private fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    return "%d:%02d".format(safe / 60, safe % 60)
}

private fun isMoogDefaultDialer(context: Context): Boolean = runCatching {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        context.getSystemService(android.app.role.RoleManager::class.java)?.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER) == true
    } else {
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage == context.packageName
    }
}.getOrDefault(false)

private fun openVoicemailCall(context: Context, number: String, account: PhoneAccountOption?) {
    if (number.isBlank()) return
    val uri = Uri.fromParts("tel", number.trim(), null)
    if (isMoogDefaultDialer(context) && account != null) {
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
