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
internal fun SettingsScreen(
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
