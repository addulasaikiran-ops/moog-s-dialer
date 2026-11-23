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
import com.moog.dialer.BuildConfig
import com.moog.dialer.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimPreferencePicker(
    phoneAccounts: List<PhoneAccountOption>,
    selectedKey: String?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = if (selectedKey == null) stringResource(R.string.system_default_ask) else phoneAccounts.firstOrNull { it.key == selectedKey }?.label ?: stringResource(R.string.system_default_ask)

    MoogListRow(
        title = stringResource(R.string.preferred_sim),
        subtitle = stringResource(R.string.outgoing_calls_selected_sim, selectedLabel),
        trailingValue = stringResource(R.string.change),
        showChevron = true,
        onClick = { expanded = true }
    )
    if (expanded) {
        ModalBottomSheet(
            onDismissRequest = { expanded = false },
            containerColor = MoogColors.surface
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.choose_calling_sim), style = MaterialTheme.typography.titleLarge, color = MoogColors.label)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.this_preference_applies_to_outgoing_calls_when_m),
                    style = MaterialTheme.typography.bodySmall,
                    color = MoogColors.secondaryLabel
                )
                Spacer(Modifier.height(12.dp))
                MoogListRow(
                    title = stringResource(R.string.system_default_ask),
                    subtitle = stringResource(R.string.system_choose_calling_account),
                    trailingValue = if (selectedKey == null) stringResource(R.string.selected) else null,
                    onClick = { onSelect(null); expanded = false }
                )
                phoneAccounts.forEach { account ->
                    MoogListRow(
                        title = account.label,
                        subtitle = stringResource(R.string.sim_calling_account),
                        trailingValue = if (selectedKey == account.key) stringResource(R.string.selected) else null,
                        onClick = { onSelect(account.key); expanded = false }
                    )
                }
                if (phoneAccounts.isEmpty()) {
                    Text(
                        stringResource(R.string.android_is_not_currently_exposing_any_sim_callin),
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

    MoogLargeTitleScaffold(title = stringResource(R.string.screen_settings)) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                MoogGroupedSection(title = stringResource(R.string.appearance), footer = stringResource(R.string.appearance_footer)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        MoogSegmentedControl(
                            options = listOf(stringResource(R.string.appearance_system), stringResource(R.string.appearance_light), stringResource(R.string.appearance_dark)),
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
                    title = stringResource(R.string.calling),
                    footer = if (isDefaultDialer) stringResource(R.string.moog_calling_sim_footer) else stringResource(R.string.android_default_phone_footer)
                ) {
                    if (isDefaultDialer) {
                        MoogListRow(
                            title = stringResource(R.string.default_phone_app),
                            subtitle = stringResource(R.string.moog_s_dialer_is_handling_phone_calls),
                            trailingValue = stringResource(R.string.active)
                        )
                    } else {
                        MoogListRow(
                            title = stringResource(R.string.default_phone_app),
                            subtitle = stringResource(R.string.set_moog_s_dialer_as_your_phone_app),
                            trailingValue = stringResource(R.string.set_up),
                            showChevron = true,
                            onClick = onRequestPhoneRole
                        )
                    }
                    if (hasPhoneStatePermission) {
                        SimPreferencePicker(phoneAccounts, selectedPhoneAccountKey, onSelectPhoneAccount)
                    } else {
                        MoogListRow(
                            title = stringResource(R.string.preferred_sim),
                            subtitle = stringResource(R.string.allow_phone_access_to_choose_an_outgoing_sim),
                            trailingValue = stringResource(R.string.allow),
                            showChevron = true,
                            onClick = onRequestPhoneState
                        )
                    }
                }
            }
            item {
                MoogGroupedSection(
                    title = stringResource(R.string.incoming_calls),
                    footer = stringResource(R.string.incoming_call_presentation_also_depends_on_andro)
                ) {
                    if (!hasNotificationPermission) {
                        MoogListRow(
                            title = stringResource(R.string.call_notifications),
                            subtitle = stringResource(R.string.allow_notifications_for_incoming_and_voicemail_e),
                            trailingValue = stringResource(R.string.allow),
                            showChevron = true,
                            onClick = onRequestNotifications
                        )
                    } else {
                        MoogListRow(
                            title = stringResource(R.string.call_notifications),
                            subtitle = stringResource(R.string.notifications_are_allowed),
                            trailingValue = stringResource(R.string.on)
                        )
                    }
                    if (Build.VERSION.SDK_INT >= 34) {
                        MoogListRow(
                            title = stringResource(R.string.full_screen_incoming_calls),
                            subtitle = if (canUseFullScreenIntent) stringResource(R.string.full_screen_intent_allowed) else stringResource(R.string.allow_incoming_over_apps),
                            trailingValue = if (canUseFullScreenIntent) stringResource(R.string.on) else stringResource(R.string.allow),
                            showChevron = !canUseFullScreenIntent,
                            onClick = if (canUseFullScreenIntent) null else onRequestFullScreenIntent
                        )
                    }
                }
            }
            item {
                MoogGroupedSection(title = stringResource(R.string.calling_features)) {
                    MoogListRow(
                        title = stringResource(R.string.voicemail),
                        subtitle = stringResource(R.string.visual_inbox_playback_and_carrier_mailbox_settin),
                        showChevron = true,
                        onClick = onOpenVoicemail
                    )
                    MoogListRow(
                        title = stringResource(R.string.spam_protection),
                        subtitle = stringResource(R.string.manage_your_on_device_blocked_numbers),
                        showChevron = true,
                        onClick = onOpenSpamProtection
                    )
                }
            }
            item {
                MoogGroupedSection(title = stringResource(R.string.about_moog), footer = stringResource(R.string.built_for_connections)) {
                    MoogListRow(title = stringResource(R.string.version), subtitle = stringResource(R.string.moog_brand), trailingValue = BuildConfig.VERSION_NAME)
                    MoogListRow(
                        title = stringResource(R.string.privacy),
                        subtitle = stringResource(R.string.contacts_and_blocklist_stay_on_this_device),
                        trailingValue = stringResource(R.string.on_device)
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.calling_sim_selection_voicemail_availability_and),
                    color = MoogColors.secondaryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
