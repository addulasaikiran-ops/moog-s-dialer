package com.moog.dialer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF101310)
private val Panel = Color(0xFF20251F)
private val Key = Color(0xFF30372D)
private val Lime = Color(0xFFB8F36A)
private val Muted = Color(0xFFA4AA9F)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MoogsDialerScreen(
                    onCall = { number ->
                        if (number.isNotBlank()) {
                            // Opens the system dialer; no call permission is needed.
                            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
                        }
                    }
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun MoogsDialerScreen(onCall: (String) -> Unit) {
    var number by remember { mutableStateOf("") }
    val keys = listOf(
        listOf("1" to "", "2" to "ABC", "3" to "DEF"),
        listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
        listOf("*" to "", "0" to "+", "#" to "")
    )

    Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("moog's dialer", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("YOUR CALLING SPACE", color = Lime, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
                }
                Box(
                    modifier = Modifier.size(44.dp).background(Panel, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Contacts, contentDescription = "Contacts", tint = Lime)
                }
            }

            Spacer(Modifier.height(28.dp))

            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("ENTER NUMBER", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(14.dp))
                BasicTextField(
                    value = number,
                    onValueChange = { number = it.filter { c -> c.isDigit() || c == '+' || c == '*' || c == '#' }.take(24) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = Color.White, fontSize = 32.sp, textAlign = TextAlign.Center, letterSpacing = 1.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (number.isEmpty()) Text("Type a number", color = Muted.copy(alpha = 0.55f), fontSize = 25.sp)
                            inner()
                        }
                    }
                )
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier.size(42.dp).background(Panel, CircleShape)
                        .clickable { if (number.isNotEmpty()) number = number.dropLast(1) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Backspace, contentDescription = "Delete last digit", tint = Muted)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                keys.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        row.forEach { (digit, letters) ->
                            Column(
                                modifier = Modifier.weight(1f).height(72.dp)
                                    .background(Key, RoundedCornerShape(22.dp))
                                    .clickable { number += digit },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(digit, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Medium)
                                if (letters.isNotEmpty()) Text(letters, color = Muted, fontSize = 9.sp, letterSpacing = 2.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier.size(66.dp).background(Lime, CircleShape)
                    .clickable { onCall(number) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Call, contentDescription = "Open phone dialer", tint = Ink, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavItem("Recents", Icons.Default.History)
                NavItem("Contacts", Icons.Default.Contacts)
                NavItem("Favorites", Icons.Default.Star)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun NavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { }) {
        Icon(icon, contentDescription = label, tint = Muted)
        Spacer(Modifier.height(4.dp))
        Text(label, color = Muted, fontSize = 11.sp)
    }
}
