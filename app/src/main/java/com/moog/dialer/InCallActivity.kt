package com.moog.dialer

import android.os.Bundle
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moog.dialer.telecom.CallSession

private val CallBlue = Color(0xFF087CFA)
private val CallInk = Color(0xFF17191D)
private val CallMuted = Color(0xFF858B96)

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
            MaterialTheme(colorScheme = androidx.compose.material3.lightColorScheme(primary = CallBlue)) {
                val calls by CallSession.calls.collectAsState()
                val current = calls.firstOrNull { it.state == Call.STATE_RINGING }
                    ?: calls.firstOrNull { it.state == Call.STATE_ACTIVE || it.state == Call.STATE_DIALING || it.state == Call.STATE_CONNECTING }
                    ?: calls.firstOrNull()

                LaunchedEffect(calls.isEmpty()) {
                    if (calls.isEmpty()) finish()
                }
                BackHandler(enabled = current != null) { /* Keep call controls available; notification can reopen this screen. */ }

                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5F6F8)) {
                    if (current == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No active call", color = CallMuted)
                        }
                    } else {
                        val incoming = current.state == Call.STATE_RINGING
                        val status = when (current.state) {
                            Call.STATE_RINGING -> "Incoming call"
                            Call.STATE_DIALING -> "Calling…"
                            Call.STATE_CONNECTING -> "Connecting…"
                            Call.STATE_ACTIVE -> "Connected"
                            Call.STATE_HOLDING -> "On hold"
                            else -> "Call"
                        }
                        Column(
                            Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 34.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("MOOG'S DIALER", color = CallMuted, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(70.dp))
                                Box(Modifier.size(112.dp).background(Color(0xFFE5F0FF), CircleShape), contentAlignment = Alignment.Center) {
                                    Text(current.number.take(1).uppercase(), color = CallBlue, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(24.dp))
                                Text(current.number, color = CallInk, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                Text(status, color = CallMuted, fontSize = 14.sp)
                            }

                            if (incoming) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                    RoundCallButton("Decline", Color(0xFFE5484D), Icons.Default.CallEnd) {
                                        CallSession.reject(current.call)
                                        finish()
                                    }
                                    RoundCallButton("Answer", Color(0xFF20A464), Icons.Default.Call) {
                                        CallSession.answer(current.call)
                                    }
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                        RoundCallButton(if (CallSession.isMuted) "Unmute" else "Mute", Color(0xFFE8EDF5), if (CallSession.isMuted) Icons.Default.MicOff else Icons.Default.Mic, CallInk) {
                                            CallSession.toggleMute()
                                        }
                                        RoundCallButton(if (CallSession.isSpeakerOn) "Speaker on" else "Speaker", Color(0xFFE8EDF5), Icons.Default.VolumeUp, CallInk) {
                                            CallSession.toggleSpeaker()
                                        }
                                        if (current.canHold) {
                                            RoundCallButton(if (current.state == Call.STATE_HOLDING) "Resume" else "Hold", Color(0xFFE8EDF5), if (current.state == Call.STATE_HOLDING) Icons.Default.PlayArrow else Icons.Default.Pause, CallInk) {
                                                CallSession.toggleHold(current.call)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(38.dp))
                                    RoundCallButton("End call", Color(0xFFE5484D), Icons.Default.CallEnd) {
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
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(66.dp),
            shape = CircleShape,
            colors = ButtonDefaults.filledIconButtonColors(containerColor = background, contentColor = tint)
        ) {
            androidx.compose.material3.Icon(icon, contentDescription = label, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = CallMuted, fontSize = 11.sp)
    }
}
