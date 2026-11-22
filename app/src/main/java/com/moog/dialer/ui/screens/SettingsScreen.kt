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
import androidx.compose.foundation.lazy.LazyColumn
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
import com.moog.dialer.ui.components.MoogLargeTitleScaffold
import com.moog.dialer.ui.components.MoogListRow
import com.moog.dialer.ui.components.MoogSegmentedControl
import com.moog.dialer.ui.components.MoogTabItem
import com.moog.dialer.ContactsViewModel
import com.moog.dialer.SpamBlockRepository
import com.moog.dialer.SpamProtectionActivity
import com.moog.dialer.VoicemailActivity
import com.moog.dialer.isMoogDefaultPhoneApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimPreferencePicker(
    phoneAccounts: List<PhoneAccountOption>,
    selectedKey: String?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = if (selectedKey == null) "System default / Ask every time"
        else phoneAccounts.firstOrNull { it.key == selectedKey }?.label ?: "System default / Ask every time"

    MoogListRow(
        title = "Preferred SIM",
        subtitle = "Outgoing calls · $selectedLabel",
        trailingValue = "Change",
        showChevron = true,
        onClick = { expanded = true }
    )
    if (expanded) {
        ModalBottomSheet(
            onDismissRequest = { expanded = false },
            containerColor = MoogColors.surface
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Choose calling SIM", style = MaterialTheme.typography.titleLarge, color = MoogColors.label)
                Spacer(Modifier.height(8.dp))
                Text(
                    "This preference applies to outgoing calls when MOOG'S DIALER is the default Phone app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MoogColors.secondaryLabel
                )
                Spacer(Modifier.height(12.dp))
                MoogListRow(
                    title = "System default / Ask every time",
                    subtitle = "Let Android choose the calling account",
                    trailingValue = if (selectedKey == null) "Selected" else null,
                    onClick = { onSelect(null); expanded = false }
                )
                phoneAccounts.forEach { account ->
                    MoogListRow(
                        title = account.label,
                        subtitle = "SIM calling account",
                        trailingValue = if (selectedKey == account.key) "Selected" else null,
                        onClick = { onSelect(account.key); expanded = false }
                    )
                }
                if (phoneAccounts.isEmpty()) {
                    Text(
                        "Android is not currently exposing any SIM calling accounts.",
                        color = MoogColors.secondaryLabel,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                Spacer(Modifier.height(28.dp))
            }
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

    MoogLargeTitleScaffold(title = "Settings") { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
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
            }
            item {
                MoogGroupedSection(
                    title = "CALLING",
                    footer = if (isDefaultDialer) "MOOG'S DIALER can apply your calling SIM preference." else "Android controls outgoing calls until MOOG'S DIALER is selected as the default Phone app."
                ) {
                    if (isDefaultDialer) {
                        MoogListRow(
                            title = "Default Phone app",
                            subtitle = "MOOG'S DIALER is handling phone calls",
                            trailingValue = "Active"
                        )
                    } else {
                        MoogListRow(
                            title = "Default Phone app",
                            subtitle = "Set MOOG'S DIALER as your phone app",
                            trailingValue = "Set up",
                            showChevron = true,
                            onClick = onRequestPhoneRole
                        )
                    }
                    if (hasPhoneStatePermission) {
                        SimPreferencePicker(phoneAccounts, selectedPhoneAccountKey, onSelectPhoneAccount)
                    } else {
                        MoogListRow(
                            title = "Preferred SIM",
                            subtitle = "Allow phone access to choose an outgoing SIM",
                            trailingValue = "Allow",
                            showChevron = true,
                            onClick = onRequestPhoneState
                        )
                    }
                }
            }
            item {
                MoogGroupedSection(
                    title = "INCOMING CALLS",
                    footer = "Incoming-call presentation also depends on Android and device manufacturer settings."
                ) {
                    if (!hasNotificationPermission) {
                        MoogListRow(
                            title = "Call notifications",
                            subtitle = "Allow notifications for incoming and voicemail events",
                            trailingValue = "Allow",
                            showChevron = true,
                            onClick = onRequestNotifications
                        )
                    } else {
                        MoogListRow(
                            title = "Call notifications",
                            subtitle = "Notifications are allowed",
                            trailingValue = "On"
                        )
                    }
                    if (Build.VERSION.SDK_INT >= 34) {
                        MoogListRow(
                            title = "Full-screen incoming calls",
                            subtitle = if (canUseFullScreenIntent) "Full-screen intent access is allowed" else "Allow the incoming call screen to appear over other apps",
                            trailingValue = if (canUseFullScreenIntent) "On" else "Allow",
                            showChevron = !canUseFullScreenIntent,
                            onClick = if (canUseFullScreenIntent) null else onRequestFullScreenIntent
                        )
                    }
                }
            }
            item {
                MoogGroupedSection(title = "CALLING FEATURES") {
                    MoogListRow(
                        title = "Voicemail",
                        subtitle = "Visual inbox, playback and carrier mailbox settings",
                        showChevron = true,
                        onClick = onOpenVoicemail
                    )
                    MoogListRow(
                        title = "Spam protection",
                        subtitle = "Manage your on-device blocked numbers",
                        showChevron = true,
                        onClick = onOpenSpamProtection
                    )
                }
            }
            item {
                MoogGroupedSection(title = "ABOUT MOOG", footer = "Built for your everyday connections.") {
                    MoogListRow(title = "Version", subtitle = "MOOG'S DIALER", trailingValue = "0.2.0")
                    MoogListRow(
                        title = "Privacy",
                        subtitle = "Contacts and blocklist stay on this device",
                        trailingValue = "On-device"
                    )
                }
            }
            item {
                Text(
                    "Calling, SIM selection, voicemail availability and full-screen presentation are provided or controlled by Android Telecom, your carrier and your device manufacturer. Some features require MOOG'S DIALER to be the default Phone app.",
                    color = MoogColors.secondaryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
