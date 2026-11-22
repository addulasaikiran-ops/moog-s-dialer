package com.moog.dialer.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val call = CallSession.primaryCall()?.call ?: return
        when (intent.action) {
            ACTION_ANSWER -> CallSession.answer(call)
            ACTION_DECLINE -> CallSession.reject(call)
            ACTION_DISCONNECT -> CallSession.disconnect(call)
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.moog.dialer.call.ANSWER"
        const val ACTION_DECLINE = "com.moog.dialer.call.DECLINE"
        const val ACTION_DISCONNECT = "com.moog.dialer.call.DISCONNECT"
    }
}
