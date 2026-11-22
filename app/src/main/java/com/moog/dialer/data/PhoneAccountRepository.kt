package com.moog.dialer.data

import android.content.Context
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

data class PhoneAccountOption(val key: String, val label: String, val handle: PhoneAccountHandle)

object PhoneAccountRepository {
    private const val PREFS = "moogs_dialer_preferences"
    private const val KEY_SELECTED_ACCOUNT = "preferred_phone_account"

    fun accountKey(handle: PhoneAccountHandle): String =
        "${handle.componentName.flattenToString()}|${handle.id}"

    fun getCallAccounts(context: Context): List<PhoneAccountOption> {
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return emptyList()
        return try {
            telecom.callCapablePhoneAccounts.mapNotNull { handle ->
                val account = telecom.getPhoneAccount(handle) ?: return@mapNotNull null
                if (!account.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) return@mapNotNull null
                val label = account.label?.toString()?.takeIf { it.isNotBlank() }
                    ?: account.shortDescription?.toString()?.takeIf { it.isNotBlank() }
                    ?: "SIM"
                PhoneAccountOption(accountKey(handle), label, handle)
            }
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: RuntimeException) {
            emptyList()
        }
    }

    fun getPreferredAccountKey(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SELECTED_ACCOUNT, null)

    fun savePreferredAccount(context: Context, key: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SELECTED_ACCOUNT, key).apply()
    }

    fun getPreferredHandle(context: Context): PhoneAccountHandle? {
        val key = getPreferredAccountKey(context) ?: return null
        val match = getCallAccounts(context).firstOrNull { it.key == key }
        if (match == null) savePreferredAccount(context, null)
        return match?.handle
    }

    fun getAccountLabels(context: Context): Map<String, String> =
        getCallAccounts(context).associate { it.key to it.label }
}
