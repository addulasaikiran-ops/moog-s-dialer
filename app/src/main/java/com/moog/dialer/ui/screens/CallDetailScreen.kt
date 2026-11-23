package com.moog.dialer.ui.screens

import androidx.compose.ui.res.stringResource
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
    val name = call.displayName.ifBlank { call.number.ifBlank { stringResource(R.string.unknown_caller) } }
    val callType = when (call.type) {
        android.provider.CallLog.Calls.INCOMING_TYPE -> stringResource(R.string.incoming)
        android.provider.CallLog.Calls.OUTGOING_TYPE -> stringResource(R.string.outgoing)
        android.provider.CallLog.Calls.MISSED_TYPE -> stringResource(R.string.missed)
        android.provider.CallLog.Calls.REJECTED_TYPE -> stringResource(R.string.rejected)
        android.provider.CallLog.Calls.BLOCKED_TYPE -> stringResource(R.string.blocked)
        android.provider.CallLog.Calls.VOICEMAIL_TYPE -> stringResource(R.string.voicemail)
        else -> stringResource(R.string.call)
    }
    val whenLabel = DateUtils.getRelativeTimeSpanString(
        call.date,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()
    val durationLabel = "%d:%02d".format(call.durationSeconds.coerceAtLeast(0L) / 60, call.durationSeconds.coerceAtLeast(0L) % 60)

    MoogLargeTitleScaffold(
        title = stringResource(R.string.screen_call_details),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back_to_recents), tint = MoogColors.blue)
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
                Text(call.number.ifBlank { stringResource(R.string.private_number) }, color = MoogColors.secondaryLabel, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    MoogCircleButton(
                        icon = Icons.Default.Sms,
                        contentDescription = stringResource(R.string.message_person, name),
                        onClick = { if (call.number.isNotBlank()) onMessage(call.number) }
                    )
                    MoogCircleButton(
                        icon = Icons.Default.Call,
                        contentDescription = stringResource(R.string.call_person, name),
                        background = MoogColors.green,
                        tint = androidx.compose.ui.graphics.Color.White,
                        onClick = { if (call.number.isNotBlank()) onDial(call.number) }
                    )
                }
            }

            MoogGroupedSection(title = stringResource(R.string.call_information)) {
                MoogListRow(title = stringResource(R.string.type), trailingValue = callType)
                MoogListRow(title = stringResource(R.string.when), trailingValue = whenLabel)
                MoogListRow(title = stringResource(R.string.duration), trailingValue = durationLabel)
                MoogListRow(title = stringResource(R.string.sim), trailingValue = call.simLabel)
            }

            MoogGroupedSection(
                title = stringResource(R.string.privacy_and_safety),
                footer = stringResource(R.string.blocking_stored_locally)
            ) {
                MoogListRow(
                    title = if (isBlocked) stringResource(R.string.unblock_this_number) else stringResource(R.string.block_this_number),
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
