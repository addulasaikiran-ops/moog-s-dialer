package com.moog.dialer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moog.dialer.ui.theme.MoogColors
import com.moog.dialer.ui.theme.MoogTheme


class SpamProtectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MoogTheme {
                var number by remember { mutableStateOf("") }
                var blocked by remember { mutableStateOf(SpamBlockRepository.blockedNumbers(this@SpamProtectionActivity)) }
                Surface(Modifier.fillMaxSize(), color = SpamPage) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { finish() }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = SpamBlue)
                            }
                            Column {
                                Text("Spam protection", color = SpamInk, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                                Text("Your personal blocklist", color = SpamMuted, fontSize = 12.sp)
                            }
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = MoogColors.surface)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("Add a number to block", color = SpamInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                BasicTextField(
                                    value = number,
                                    onValueChange = { number = it },
                                    modifier = Modifier.fillMaxWidth().background(SpamPage, RoundedCornerShape(12.dp)).padding(14.dp),
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(color = SpamInk, fontSize = 16.sp),
                                    decorationBox = { inner ->
                                        if (number.isBlank()) Text("Enter phone number", color = SpamMuted, fontSize = 14.sp)
                                        inner()
                                    }
                                )
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        if (SpamBlockRepository.block(this@SpamProtectionActivity, number)) {
                                            blocked = SpamBlockRepository.blockedNumbers(this@SpamProtectionActivity)
                                            number = ""
                                        }
                                    },
                                    enabled = number.any(Char::isDigit),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Block number") }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("BLOCKED NUMBERS", color = SpamMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.3.sp, modifier = Modifier.weight(1f))
                            Text("${blocked.size}", color = SpamBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(8.dp))
                        if (blocked.isEmpty()) {
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                                    Text("—", color = SpamMuted, fontSize = 24.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Text("Your blocklist is empty", color = SpamInk, fontWeight = FontWeight.SemiBold)
                                    Text("Numbers you add here will be rejected when they call while MOOG'S DIALER handles incoming calls.", color = SpamMuted, fontSize = 12.sp)
                                }
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(blocked, key = { it }) { item ->
                                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(item, color = SpamInk, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                            OutlinedButton(onClick = {
                                                SpamBlockRepository.unblock(this@SpamProtectionActivity, item)
                                                blocked = SpamBlockRepository.blockedNumbers(this@SpamProtectionActivity)
                                            }) { Text("Unblock") }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Only numbers you add are blocked. MOOG'S DIALER does not use a live caller-ID or spam-reputation service. Private or withheld numbers cannot be matched.",
                            color = SpamMuted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}
