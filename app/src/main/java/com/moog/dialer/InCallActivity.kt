package com.moog.dialer

import android.os.Bundle
import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moog.dialer.telecom.CallSession
import com.moog.dialer.SpamBlockRepository
import com.moog.dialer.ui.theme.MoogColors
import com.moog.dialer.ui.theme.MoogTheme
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay



private suspend fun resolveInCallContactName(context: Context, number: String): String? = withContext(Dispatchers.IO) {
    if (number.isBlank() || number == "Private number") return@withContext null
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
        return@withContext null
    }
    runCatching {
        val lookupUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )
        context.contentResolver.query(
            lookupUri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
        }
    }.getOrNull()
}

private fun formatCallTimer(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

class InCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        setContent {
            MoogTheme(forceDark = true) {
                var showDtmf by remember { mutableStateOf(false) }
                val calls by CallSession.calls.collectAsState()
                val audio by CallSession.audio.collectAsState()
                // Keep the established foreground call on screen when a second call rings.
                val current = calls.firstOrNull { it.state == Call.STATE_ACTIVE }
                    ?: calls.firstOrNull { it.state == Call.STATE_DIALING || it.state == Call.STATE_CONNECTING }
                    ?: calls.firstOrNull { it.state == Call.STATE_HOLDING }
                    ?: calls.firstOrNull { it.state == Call.STATE_RINGING }
                val waitingCall = calls.firstOrNull { it.state == Call.STATE_RINGING && it.call !== current?.call }
                val heldCall = calls.firstOrNull { it.state == Call.STATE_HOLDING && it.call !== current?.call }
                val conferenceTarget = current?.conferenceableCalls
                    ?.firstOrNull { candidate -> calls.any { it.call === candidate } }
                    ?.let { candidate -> calls.firstOrNull { it.call === candidate } }
                    ?: calls.firstOrNull { entry ->
                        entry.call !== current?.call && current != null && current.call in entry.conferenceableCalls
                    }

                LaunchedEffect(calls.isEmpty()) {
                    if (calls.isEmpty()) finish()
                }

                Surface(modifier = Modifier.fillMaxSize(), color = MoogColors.background) {
                    if (current == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No active call", color = MoogColors.secondaryLabel)
                        }
                    } else {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val displayName by produceState(initialValue = current.number, current.number) {
                            value = resolveInCallContactName(context, current.number) ?: current.number
                        }
                        var elapsedSeconds by remember(current.call) { mutableLongStateOf(0L) }
                        LaunchedEffect(current.call, current.state) {
                            if (current.state == Call.STATE_ACTIVE || current.state == Call.STATE_HOLDING) {
                                val connectedAt = current.call.details.connectTimeMillis
                                while (true) {
                                    elapsedSeconds = if (connectedAt > 0L) {
                                        ((System.currentTimeMillis() - connectedAt) / 1000L).coerceAtLeast(0L)
                                    } else 0L
                                    delay(1000L)
                                }
                            } else {
                                elapsedSeconds = 0L
                            }
                        }
                        val incoming = current.state == Call.STATE_RINGING
                        val status = when (current.state) {
                            Call.STATE_RINGING -> "Incoming call"
                            Call.STATE_DIALING -> "Calling…"
                            Call.STATE_CONNECTING -> "Connecting…"
                            Call.STATE_ACTIVE -> "Connected"
                            Call.STATE_HOLDING -> "On hold"
                            else -> "Call"
                        }
                        val speakerAvailable = audio.availableRoutes and CallAudioState.ROUTE_SPEAKER != 0
                        val canSendDtmf = current.state == Call.STATE_ACTIVE || current.state == Call.STATE_HOLDING
                        LaunchedEffect(current.call, current.state) {
                            showDtmf = false
                            CallSession.stopDtmfTone()
                        }
                        Column(
                            Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 34.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("MOOG'S DIALER", color = MoogColors.secondaryLabel, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(70.dp))
                                Box(Modifier.size(112.dp).background(MoogColors.fill, CircleShape), contentAlignment = Alignment.Center) {
                                    val initials = displayName.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("").uppercase()
                                    Text(initials.ifBlank { "?" }, color = MoogColors.blue, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(24.dp))
                                Text(displayName, color = MoogColors.label, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                Text(status, color = MoogColors.secondaryLabel, fontSize = 14.sp)
                                if (current.state == Call.STATE_ACTIVE || current.state == Call.STATE_HOLDING) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(formatCallTimer(elapsedSeconds), color = MoogColors.secondaryLabel, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(current.simLabel, color = MoogColors.blue, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                if (current.conferenceChildCount > 0) {
                                    Spacer(Modifier.height(8.dp))
                                    Text("Conference · ${current.conferenceChildCount} participants", color = MoogColors.blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            if (incoming) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                    RoundCallButton("Block & decline", MoogColors.red, Icons.Default.CallEnd) {
                                        SpamBlockRepository.block(this@InCallActivity, current.number)
                                        CallSession.reject(current.call)
                                        finish()
                                    }
                                    RoundCallButton("Answer", MoogColors.green, Icons.Default.Call) {
                                        CallSession.answer(current.call)
                                    }
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (waitingCall != null) {
                                        WaitingCallCard(
                                            number = waitingCall.number,
                                            simLabel = waitingCall.simLabel,
                                            onAnswer = { CallSession.answer(waitingCall.call) },
                                            onDecline = { CallSession.reject(waitingCall.call) }
                                        )
                                        Spacer(Modifier.height(16.dp))
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                        if (canSendDtmf) {
                                            RoundCallButton(if (showDtmf) "Hide keypad" else "Keypad", MoogColors.fill, Icons.Default.Dialpad, MoogColors.label) {
                                                showDtmf = !showDtmf
                                                if (!showDtmf) CallSession.stopDtmfTone()
                                            }
                                        }
                                        RoundCallButton(if (audio.isMuted) "Unmute" else "Mute", MoogColors.fill, if (audio.isMuted) Icons.Default.MicOff else Icons.Default.Mic, MoogColors.label) {
                                            CallSession.toggleMute()
                                        }
                                        RoundCallButton(
                                            if (audio.isSpeakerOn) "Speaker on" else "Speaker",
                                            MoogColors.fill,
                                            Icons.Default.VolumeUp,
                                            MoogColors.label,
                                            enabled = speakerAvailable
                                        ) {
                                            CallSession.toggleSpeaker()
                                        }
                                        if (current.canHold) {
                                            RoundCallButton(if (current.state == Call.STATE_HOLDING) "Resume" else "Hold", MoogColors.fill, if (current.state == Call.STATE_HOLDING) Icons.Default.PlayArrow else Icons.Default.Pause, MoogColors.label) {
                                                CallSession.toggleHold(current.call)
                                            }
                                        }
                                    }
                                    if (heldCall != null || conferenceTarget != null) {
                                        Spacer(Modifier.height(18.dp))
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                            if (heldCall != null && current.state == Call.STATE_ACTIVE && current.canHold && heldCall.canHold) {
                                                RoundCallButton("Swap", MoogColors.fill, Icons.Default.PlayArrow, MoogColors.label) {
                                                    CallSession.swapCalls(current.call, heldCall.call)
                                                }
                                            }
                                            if (conferenceTarget != null && current.state == Call.STATE_ACTIVE) {
                                                RoundCallButton("Merge", MoogColors.fill, Icons.Default.Call, MoogColors.label) {
                                                    CallSession.conference(current.call, conferenceTarget.call)
                                                }
                                            }
                                        }
                                    }
                                    if (showDtmf) {
                                        Spacer(Modifier.height(24.dp))
                                        DtmfKeypad(onDigit = { digit -> CallSession.sendDtmfTone(current.call, digit) })
                                    }
                                    Spacer(Modifier.height(38.dp))
                                    RoundCallButton("End call", MoogColors.red, Icons.Default.CallEnd) {
                                        CallSession.disconnect(current.call)
                                        finish()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun RoundCallButton(
    label: String,
    background: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = Color.White,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.FilledIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = background, contentColor = tint)
        ) {
            androidx.compose.material3.Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = if (enabled) MoogColors.secondaryLabel else MoogColors.secondaryLabel.copy(alpha = 0.45f), fontSize = 11.sp)
    }
}


@androidx.compose.runtime.Composable
private fun DtmfKeypad(onDigit: (Char) -> Unit) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('*', '0', '#')
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                row.forEach { digit ->
                    androidx.compose.material3.FilledTonalButton(
                        onClick = { onDigit(digit) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                    ) {
                        Text(digit.toString(), color = MoogColors.label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}


@androidx.compose.runtime.Composable
private fun WaitingCallCard(
    number: String,
    simLabel: String,
    onAnswer: () -> Unit,
    onDecline: () -> Unit
) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MoogColors.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("Call waiting", color = MoogColors.blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(number, color = MoogColors.label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(simLabel, color = MoogColors.secondaryLabel, fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.FilledTonalButton(
                    onClick = onDecline,
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(contentColor = MoogColors.red)
                ) {
                    Text("Decline")
                }
                androidx.compose.material3.Button(
                    onClick = onAnswer,
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MoogColors.green)
                ) {
                    Text("Answer")
                }
            }
        }
    }
}
