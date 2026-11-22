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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ContactsScreen(
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
