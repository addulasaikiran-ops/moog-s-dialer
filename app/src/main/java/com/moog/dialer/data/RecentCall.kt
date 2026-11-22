package com.moog.dialer.data

import android.content.ContentResolver
import android.provider.CallLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RecentCall(
    val id: Long,
    val number: String,
    val displayName: String,
    val type: Int,
    val date: Long,
    val durationSeconds: Long
)

class CallLogRepository(private val contentResolver: ContentResolver) {
    suspend fun getRecentCalls(limit: Int = 100): List<RecentCall> = withContext(Dispatchers.IO) {
        val calls = mutableListOf<RecentCall>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )
        contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
            val numberColumn = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val nameColumn = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val typeColumn = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val dateColumn = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durationColumn = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            while (cursor.moveToNext() && calls.size < limit) {
                calls += RecentCall(
                    id = cursor.getLong(idColumn),
                    number = cursor.getString(numberColumn).orEmpty(),
                    displayName = cursor.getString(nameColumn).orEmpty(),
                    type = cursor.getInt(typeColumn),
                    date = cursor.getLong(dateColumn),
                    durationSeconds = cursor.getLong(durationColumn)
                )
            }
        }
        calls
    }
}
