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
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MoogTheme {
                MoogsDialerApp(
                    initialNumber = intent?.data?.schemeSpecificPart.orEmpty(),
                    onDial = { value ->
                        val number = value.trim()
                        if (number.isNotEmpty()) {
                            val isDefaultDialer = isMoogDefaultPhoneApp(this@MainActivity)
                            if (isDefaultDialer) {
                                try {
                                    val extras = Bundle()
                                    if (
                                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
                                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        PhoneAccountRepository.getPreferredHandle(this@MainActivity)?.let { handle ->
                                            extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                                        }
                                    }
                                    getSystemService(TelecomManager::class.java)
                                        .placeCall(Uri.fromParts("tel", number, null), extras)
                                } catch (_: SecurityException) {
                                    startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
                                }
                            } else {
                                startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
                            }
                        }
                    }
                )
            }
        }
    }
}
