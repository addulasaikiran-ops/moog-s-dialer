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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun KeypadScreen(
    number: String,
    contacts: List<PhoneContact>,
    onNumberChange: (String) -> Unit,
    onDelete: () -> Unit,
    onDial: () -> Unit,
    onChooseContact: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptics = LocalHapticFeedback.current
    val keys = listOf(
        listOf("1" to "", "2" to "ABC", "3" to "DEF"),
        listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
        listOf("*" to "", "0" to "+", "#" to "")
    )
    val suggestions = remember(number, contacts) {
        if (number.isBlank()) emptyList()
        else contacts.filter { T9Search.matches(it, number) }.take(3)
    }
    val numberSize = when {
        number.length <= 8 -> 34.sp
        number.length <= 12 -> 30.sp
        number.length <= 16 -> 26.sp
        else -> 22.sp
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        if (suggestions.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { contact ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(MoogColors.surface)
                            .clickable { onChooseContact(contact.phoneNumber) }
                            .padding(horizontal = 9.dp, vertical = 8.dp)
                    ) {
                        Text(
                            contact.displayName,
                            color = MoogColors.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text(
                            contact.phoneNumber,
                            color = MoogColors.secondaryLabel,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Box(
            Modifier.fillMaxWidth().height(62.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = number,
                onValueChange = {
                    onNumberChange(it.filter { c -> c.isDigit() || c == '+' || c == '*' || c == '#' }.take(24))
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                textStyle = TextStyle(
                    color = MoogColors.label,
                    fontSize = numberSize,
                    fontFamily = MaterialTheme.typography.displayLarge.fontFamily,
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.5.sp
                ),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (number.isEmpty()) {
                            Text(
                                stringResource(R.string.enter_a_number),
                                color = MoogColors.secondaryLabel,
                                fontSize = 22.sp
                            )
                        }
                        inner()
                    }
                }
            )
        }

        if (number.isNotBlank()) {
            TextButton(
                onClick = {
                    val addContact = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                        type = ContactsContract.RawContacts.CONTENT_TYPE
                        putExtra(ContactsContract.Intents.Insert.PHONE, number)
                    }
                    runCatching { context.startActivity(addContact) }
                },
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text(stringResource(R.string.add_number), color = MoogColors.blue, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Spacer(Modifier.height(44.dp))
        }

        Spacer(Modifier.weight(1f))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            keys.forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { (digit, letters) ->
                        val interactionSource = remember(digit) { MutableInteractionSource() }
                        val pressed by interactionSource.collectIsPressedAsState()
                        val keyColor by animateColorAsState(
                            targetValue = if (pressed) MoogColors.separator else MoogColors.keyFill,
                            label = "key-press"
                        )
                        Box(
                            Modifier.size(78.dp)
                                .clip(CircleShape)
                                .background(keyColor)
                                .combinedClickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onNumberChange(number + digit)
                                    },
                                    onLongClick = if (digit == "0") {
                                        {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onNumberChange(number + "+")
                                        }
                                    } else null
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    digit,
                                    color = MoogColors.label,
                                    fontSize = 36.sp,
                                    fontFamily = MaterialTheme.typography.displayLarge.fontFamily,
                                    fontWeight = FontWeight.Light
                                )
                                if (letters.isNotEmpty()) {
                                    Text(
                                        letters,
                                        color = MoogColors.secondaryLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(86.dp)) {
            Box(
                Modifier.align(Alignment.Center).size(78.dp).clip(CircleShape)
                    .background(MoogColors.green)
                    .clickable(onClick = onDial),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Call,
                    contentDescription = stringResource(R.string.call_entered_number),
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
            if (number.isNotEmpty()) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.align(Alignment.CenterEnd).size(52.dp)
                ) {
                    Icon(
                        Icons.Default.Backspace,
                        contentDescription = stringResource(R.string.delete_last_digit),
                        tint = MoogColors.secondaryLabel
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
