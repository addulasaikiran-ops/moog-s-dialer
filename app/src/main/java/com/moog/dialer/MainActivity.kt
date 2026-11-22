package com.moog.dialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.moog.dialer.data.PhoneAccountRepository
import com.moog.dialer.ui.screens.MoogsDialerApp
import com.moog.dialer.ui.theme.MoogTheme

internal fun isMoogDefaultPhoneApp(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_DIALER) == true
    } else {
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage == context.packageName
    }
}

class MainActivity : ComponentActivity() {
    private fun openSystemDialer(number: String) {
        startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
    }

    private fun placeDirectCall(number: String) {
        // Keep a permission check immediately adjacent to placeCall so the
        // direct-call path is only used after Android grants CALL_PHONE.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            openSystemDialer(number)
            return
        }
        try {
            val extras = Bundle()
            if (
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
            ) {
                PhoneAccountRepository.getPreferredHandle(this)?.let { handle ->
                    extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                }
            }
            getSystemService(TelecomManager::class.java)
                .placeCall(Uri.fromParts("tel", number, null), extras)
        } catch (_: SecurityException) {
            openSystemDialer(number)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            MoogTheme {
                var pendingCallNumber by remember { mutableStateOf<String?>(null) }
                val callPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    val number = pendingCallNumber
                    pendingCallNumber = null
                    if (number != null) {
                        if (granted && isMoogDefaultPhoneApp(this@MainActivity)) {
                            placeDirectCall(number)
                        } else {
                            openSystemDialer(number)
                        }
                    }
                }

                MoogsDialerApp(
                    initialNumber = intent?.data?.schemeSpecificPart.orEmpty(),
                    onDial = { value ->
                        val number = value.trim()
                        if (number.isNotEmpty()) {
                            if (!isMoogDefaultPhoneApp(this@MainActivity)) {
                                openSystemDialer(number)
                            } else if (
                                ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.CALL_PHONE
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                placeDirectCall(number)
                            } else {
                                pendingCallNumber = number
                                callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                            }
                        }
                    }
                )
            }
        }
    }
}
