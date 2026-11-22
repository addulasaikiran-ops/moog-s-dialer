package com.moog.dialer.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.moog.dialer.SpamBlockRepository
import com.moog.dialer.data.RecentCall
import com.moog.dialer.ui.components.MoogCircleButton
import com.moog.dialer.ui.components.MoogGroupedSection
import com.moog.dialer.ui.components.MoogLargeTitleScaffold
import com.moog.dialer.ui.components.MoogListRow
import com.moog.dialer.ui.theme.MoogColors

@Composable
internal fun CallDetailScreen(
    call: RecentCall,
    onBack: () -> Unit,
    onDial: (String) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var isBlocked by remember(call.number) {
        mutableStateOf(SpamBlockRepository.isBlocked(context, call.number))
    }
    val name = call.displayName.ifBlank { call.number.ifBlank { "Unknown caller" } }
    val callType = when (call.type) {
        android.provider.CallLog.Calls.INCOMING_TYPE -> "Incoming"
        android.provider.CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
        android.provider.CallLog.Calls.MISSED_TYPE -> "Missed"
        android.provider.CallLog.Calls.REJECTED_TYPE -> "Rejected"
        android.provider.CallLog.Calls.BLOCKED_TYPE -> "Blocked"
        android.provider.CallLog.Calls.VOICEMAIL_TYPE -> "Voicemail"
        else -> "Call"
    }
    val whenLabel = DateUtils.getRelativeTimeSpanString(
        call.date,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()
    val durationLabel = "%d:%02d".format(call.durationSeconds.coerceAtLeast(0L) / 60, call.durationSeconds.coerceAtLeast(0L) % 60)

    MoogLargeTitleScaffold(
        title = "Call Details",
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to Recents", tint = MoogColors.blue)
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Avatar(name)
                Spacer(Modifier.height(12.dp))
                Text(name, color = MoogColors.label, style = MaterialTheme.typography.titleLarge)
                Text(call.number.ifBlank { "Private number" }, color = MoogColors.secondaryLabel, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    MoogCircleButton(
                        icon = Icons.Default.Sms,
                        contentDescription = "Message $name",
                        onClick = { if (call.number.isNotBlank()) onMessage(call.number) }
                    )
                    MoogCircleButton(
                        icon = Icons.Default.Call,
                        contentDescription = "Call $name",
                        background = MoogColors.green,
                        tint = androidx.compose.ui.graphics.Color.White,
                        onClick = { if (call.number.isNotBlank()) onDial(call.number) }
                    )
                }
            }

            MoogGroupedSection(title = "CALL INFORMATION") {
                MoogListRow(title = "Type", trailingValue = callType)
                MoogListRow(title = "When", trailingValue = whenLabel)
                MoogListRow(title = "Duration", trailingValue = durationLabel)
                MoogListRow(title = "SIM", trailingValue = call.simLabel)
            }

            MoogGroupedSection(
                title = "PRIVACY AND SAFETY",
                footer = "Blocking is stored locally on this device. Private or withheld numbers cannot be matched."
            ) {
                MoogListRow(
                    title = if (isBlocked) "Unblock this number" else "Block this number",
                    subtitle = call.number,
                    destructive = !isBlocked,
                    onClick = {
                        if (isBlocked) SpamBlockRepository.unblock(context, call.number)
                        else SpamBlockRepository.block(context, call.number)
                        isBlocked = SpamBlockRepository.isBlocked(context, call.number)
                    }
                )
            }
        }
    }
}
