package com.moog.dialer.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.VoicemailContract
import android.telecom.PhoneAccountHandle
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

data class VoicemailMessage(
    val id: Long,
    val number: String,
    val dateMillis: Long,
    val durationSeconds: Long,
    val isRead: Boolean,
    val hasContent: Boolean,
    val accountLabel: String,
    val sourcePackage: String,
    val contentUri: Uri
)

sealed interface VoicemailLoadResult {
    data class Available(val messages: List<VoicemailMessage>) : VoicemailLoadResult
    data object PermissionRequired : VoicemailLoadResult
    data object ProviderUnavailable : VoicemailLoadResult
}

object VoicemailRepository {
    private const val PREFS = "moogs_voicemail_preferences"
    private const val KEY_DEFAULT_NUMBER = "default_voicemail_number"
    private const val KEY_NUMBER_PREFIX = "voicemail_number_"

    private val projection = arrayOf(
        VoicemailContract.Voicemails._ID,
        VoicemailContract.Voicemails.NUMBER,
        VoicemailContract.Voicemails.DATE,
        VoicemailContract.Voicemails.DURATION,
        VoicemailContract.Voicemails.IS_READ,
        VoicemailContract.Voicemails.HAS_CONTENT,
        VoicemailContract.Voicemails.PHONE_ACCOUNT_COMPONENT_NAME,
        VoicemailContract.Voicemails.PHONE_ACCOUNT_ID,
        VoicemailContract.Voicemails.SOURCE_PACKAGE
    )

    fun hasVoicemailPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ADD_VOICEMAIL) ==
            PackageManager.PERMISSION_GRANTED

    fun load(context: Context): VoicemailLoadResult {
        if (!hasVoicemailPermission(context)) return VoicemailLoadResult.PermissionRequired
        return try {
            val messages = mutableListOf<VoicemailMessage>()
            val cursor = context.contentResolver.query(
                VoicemailContract.Voicemails.CONTENT_URI,
                projection,
                null,
                null,
                "${VoicemailContract.Voicemails.DATE} DESC"
            ) ?: return VoicemailLoadResult.ProviderUnavailable

            cursor.use {
                val idIndex = it.getColumnIndex(VoicemailContract.Voicemails._ID)
                val numberIndex = it.getColumnIndex(VoicemailContract.Voicemails.NUMBER)
                val dateIndex = it.getColumnIndex(VoicemailContract.Voicemails.DATE)
                val durationIndex = it.getColumnIndex(VoicemailContract.Voicemails.DURATION)
                val readIndex = it.getColumnIndex(VoicemailContract.Voicemails.IS_READ)
                val contentIndex = it.getColumnIndex(VoicemailContract.Voicemails.HAS_CONTENT)
                val componentIndex = it.getColumnIndex(VoicemailContract.Voicemails.PHONE_ACCOUNT_COMPONENT_NAME)
                val accountIdIndex = it.getColumnIndex(VoicemailContract.Voicemails.PHONE_ACCOUNT_ID)
                val sourceIndex = it.getColumnIndex(VoicemailContract.Voicemails.SOURCE_PACKAGE)

                while (it.moveToNext()) {
                    val id = if (idIndex >= 0) it.getLong(idIndex) else continue
                    val component = if (componentIndex >= 0) it.getString(componentIndex).orEmpty() else ""
                    val accountId = if (accountIdIndex >= 0) it.getString(accountIdIndex).orEmpty() else ""
                    val accountLabel = if (component.isNotBlank() && accountId.isNotBlank()) {
                        "${component.substringAfterLast('.')} · ${accountId}"
                    } else {
                        "SIM not identified"
                    }
                    messages += VoicemailMessage(
                        id = id,
                        number = if (numberIndex >= 0) it.getString(numberIndex).orEmpty() else "",
                        dateMillis = if (dateIndex >= 0) it.getLong(dateIndex) else 0L,
                        durationSeconds = if (durationIndex >= 0) it.getLong(durationIndex) else 0L,
                        isRead = readIndex >= 0 && it.getInt(readIndex) != 0,
                        hasContent = contentIndex >= 0 && it.getInt(contentIndex) != 0,
                        accountLabel = accountLabel,
                        sourcePackage = if (sourceIndex >= 0) it.getString(sourceIndex).orEmpty() else "",
                        contentUri = ContentUris.withAppendedId(VoicemailContract.Voicemails.CONTENT_URI, id)
                    )
                }
            }
            VoicemailLoadResult.Available(messages)
        } catch (_: SecurityException) {
            VoicemailLoadResult.PermissionRequired
        } catch (_: RuntimeException) {
            VoicemailLoadResult.ProviderUnavailable
        }
    }

    fun markRead(context: Context, message: VoicemailMessage, read: Boolean): Boolean = runCatching {
        val values = ContentValues().apply {
            put(VoicemailContract.Voicemails.IS_READ, if (read) 1 else 0)
        }
        context.contentResolver.update(message.contentUri, values, null, null) > 0
    }.getOrDefault(false)

    fun delete(context: Context, message: VoicemailMessage): Boolean = runCatching {
        context.contentResolver.delete(message.contentUri, null, null) > 0
    }.getOrDefault(false)

    fun requestSync(context: Context, handle: PhoneAccountHandle? = null) {
        val intent = android.content.Intent(VoicemailContract.ACTION_SYNC_VOICEMAIL)
        if (handle != null) intent.putExtra(VoicemailContract.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
        context.sendBroadcast(intent)
    }

    fun saveNumber(context: Context, accountKey: String?, number: String) {
        val key = if (accountKey.isNullOrBlank()) KEY_DEFAULT_NUMBER else KEY_NUMBER_PREFIX + accountKey
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, number.trim()).apply()
    }

    fun getNumber(context: Context, accountKey: String? = null): String {
        val key = if (accountKey.isNullOrBlank()) KEY_DEFAULT_NUMBER else KEY_NUMBER_PREFIX + accountKey
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, "").orEmpty()
    }

    fun resolveDeviceVoicemailNumber(context: Context): String? {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) return null
        return runCatching {
            context.getSystemService(TelephonyManager::class.java)?.voiceMailNumber
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
