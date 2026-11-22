package com.moog.dialer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moog.dialer.ui.components.MoogEmptyState
import com.moog.dialer.ui.components.MoogGroupedSection
import com.moog.dialer.ui.components.MoogLargeTitleScaffold
import com.moog.dialer.ui.components.MoogListRow
import com.moog.dialer.ui.theme.MoogColors
import com.moog.dialer.ui.theme.MoogTheme

class SpamProtectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MoogTheme {
                SpamProtectionScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun SpamProtectionScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var number by remember { mutableStateOf("") }
    var editMode by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(SpamBlockRepository.blockedNumbers(context)) }

    MoogLargeTitleScaffold(
        title = "Spam Protection",
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MoogColors.blue)
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            MoogGroupedSection(title = "ADD A NUMBER", footer = "Only numbers you add here are blocked.") {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    OutlinedTextField(
                        value = number,
                        onValueChange = { number = it.filter { char -> char.isDigit() || char == '+' || char == ' ' || char == '-' }.take(24) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Phone number") },
                        placeholder = { Text("Enter number to block") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (number.count(Char::isDigit) >= 3 && SpamBlockRepository.block(context, number)) {
                                blocked = SpamBlockRepository.blockedNumbers(context)
                                number = ""
                            }
                        },
                        enabled = number.count(Char::isDigit) >= 3,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Block Number")
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "BLOCKED NUMBERS · ${blocked.size}",
                    color = MoogColors.secondaryLabel,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { editMode = !editMode }, enabled = blocked.isNotEmpty()) {
                    Text(if (editMode) "Done" else "Edit")
                }
            }

            if (blocked.isEmpty()) {
                MoogEmptyState(
                    title = "Your blocklist is empty",
                    message = "Numbers you add here will be rejected when MOOG'S DIALER handles incoming calls."
                )
            } else {
                MoogGroupedSection(
                    footer = "Blocked numbers are stored locally on this device."
                ) {
                    blocked.forEach { item ->
                        MoogListRow(
                            title = item,
                            leading = if (editMode) {
                                {
                                    Icon(Icons.Default.RemoveCircle, contentDescription = "Unblock $item", tint = MoogColors.red)
                                }
                            } else null,
                            trailing = if (editMode) {
                                {
                                    TextButton(onClick = {
                                        SpamBlockRepository.unblock(context, item)
                                        blocked = SpamBlockRepository.blockedNumbers(context)
                                        if (blocked.isEmpty()) editMode = false
                                    }) {
                                        Text("Unblock", color = MoogColors.red)
                                    }
                                }
                            } else null,
                            onClick = if (editMode) {
                                {
                                    SpamBlockRepository.unblock(context, item)
                                    blocked = SpamBlockRepository.blockedNumbers(context)
                                }
                            } else null
                        )
                    }
                }
            }

            Text(
                "Only numbers you add are blocked. MOOG'S DIALER does not use a live caller-ID or spam-reputation service. Private or withheld numbers cannot be matched.",
                color = MoogColors.secondaryLabel,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
        }
    }
}
