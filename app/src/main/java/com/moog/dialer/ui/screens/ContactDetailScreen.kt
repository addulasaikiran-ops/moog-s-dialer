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

private fun normalizedPhoneNumber(number: String): String =
    number.filter { it.isDigit() }.let { digits ->
        if (digits.length > 10) digits.takeLast(10) else digits
    }

@Composable
internal fun ContactDetailScreen(
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
                        Avatar(contact.displayName, contact.photoUri)
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
