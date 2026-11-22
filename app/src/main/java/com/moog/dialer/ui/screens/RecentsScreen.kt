package com.moog.dialer.ui.screens

import androidx.compose.ui.res.stringResource
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

private data class RecentCallGroup(val key: String, val calls: List<RecentCall>)

@Composable
internal fun RecentsScreen(
    calls: List<RecentCall>,
    loading: Boolean,
    isDefaultDialer: Boolean,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onDial: (String) -> Unit,
    onOpenDetails: (RecentCall) -> Unit
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
        ScreenTitle(stringResource(R.string.screen_recents), stringResource(R.string.your_recent_calls))
        when {
            !isDefaultDialer -> CenterMessage(
                stringResource(R.string.default_phone_app_required),
                stringResource(R.string.android_protects_call_history_moog_s_dialer_can_)
            )
            !hasPermission -> PermissionCard(
                stringResource(R.string.allow_call_history_access),
                stringResource(R.string.call_history_permission_rationale),
                stringResource(R.string.allow_call_history),
                onRequestPermission
            )
            loading -> CenterMessage(stringResource(R.string.loading_recent_calls), stringResource(R.string.reading_call_history))
            else -> {
                SearchField(query, { query = it }, stringResource(R.string.search_name_or_phone_number))
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MoogSegmentedControl(
                        options = listOf("All", "Missed"),
                        selectedIndex = when (filter) { "All" -> 0; "Missed" -> 1; else -> -1 },
                        onSelected = { filter = if (it == 1) "Missed" else "All" },
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        IconButton(onClick = { filterMenuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_call_filters), tint = MoogColors.secondaryLabel)
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
                                text = { Text(stringResource(R.string.all_calls)) },
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
                                onOpenDetails = { onOpenDetails(latestCall) },
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
    onMessage: () -> Unit,
    onOpenDetails: () -> Unit
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
                Text(stringResource(R.string.blocked_by_you), color = MoogColors.red, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        IconButton(onClick = onOpenDetails) {
            Icon(Icons.Default.Info, contentDescription = "Call details for ${call.number}", tint = MoogColors.secondaryLabel, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onMessage, enabled = call.number.isNotBlank()) {
            Icon(Icons.Default.Sms, contentDescription = "Message ${call.number}", tint = MoogColors.blue, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDial, enabled = call.number.isNotBlank()) {
            Icon(Icons.Default.Call, contentDescription = "Call ${call.number}", tint = MoogColors.blue, modifier = Modifier.size(18.dp))
        }
    }
}
