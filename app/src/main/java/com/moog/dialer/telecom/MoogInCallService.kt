package com.moog.dialer.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.moog.dialer.InCallActivity
import com.moog.dialer.SpamBlockRepository

class MoogInCallService : InCallService() {
    override fun onCreate() {
        super.onCreate()
        createCallChannel()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallSession.attach(this, call)
        val incomingNumber = call.details.handle?.schemeSpecificPart.orEmpty()
        if (call.state == Call.STATE_RINGING && SpamBlockRepository.isBlocked(this, incomingNumber)) {
            // Only numbers explicitly added by the user are automatically rejected.
            runCatching { call.reject(false, null) }
            return
        }
        refreshCallNotification()

        // Outgoing calls originate from the foreground dialer. Bring MOOG's
        // in-call UI forward immediately instead of leaving the user on the
        // dial pad or relying on a notification tap.
        if (call.state == Call.STATE_DIALING || call.state == Call.STATE_CONNECTING) {
            runCatching {
                startActivity(
                    Intent(this, InCallActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                )
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        CallSession.detach(call)
        super.onCallRemoved(call)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        CallSession.updateAudioState(audioState)
    }

    fun refreshCallNotification() {
        val entry = CallSession.primaryCall() ?: return
        val manager = getSystemService(NotificationManager::class.java)
        val isIncoming = entry.state == Call.STATE_RINGING
        val openIntent = PendingIntent.getActivity(
            this,
            100,
            Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val answerIntent = actionIntent(CallActionReceiver.ACTION_ANSWER, 101)
        val declineIntent = actionIntent(CallActionReceiver.ACTION_DECLINE, 102)
        val hangupIntent = actionIntent(CallActionReceiver.ACTION_DISCONNECT, 103)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(if (isIncoming) "Incoming call" else "Call in progress")
            .setContentText(entry.number)
            .setCategory(Notification.CATEGORY_CALL)
            .setPriority(Notification.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .setFullScreenIntent(openIntent, isIncoming)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val person = Person.Builder().setName(entry.number).build()
            val style = if (isIncoming) {
                Notification.CallStyle.forIncomingCall(person, declineIntent, answerIntent)
            } else {
                Notification.CallStyle.forOngoingCall(person, hangupIntent)
            }
            builder.setStyle(style)
        } else {
            if (isIncoming) {
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declineIntent)
                builder.addAction(android.R.drawable.sym_action_call, "Answer", answerIntent)
            } else {
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "End call", hangupIntent)
            }
        }
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    fun clearCallNotification() {
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, CallActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createCallChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming and ongoing call alerts"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "moog_calls"
        private const val NOTIFICATION_ID = 4201
    }
}
