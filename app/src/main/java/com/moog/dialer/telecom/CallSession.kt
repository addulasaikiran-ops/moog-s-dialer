package com.moog.dialer.telecom

import android.telecom.Call
import android.telecom.CallAudioState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CallAudioUiState(val isMuted: Boolean = false, val isSpeakerOn: Boolean = false)

data class CallUiEntry(
    val call: Call,
    val number: String,
    val state: Int,
    val canHold: Boolean
)

object CallSession {
    private val entries = LinkedHashMap<Call, CallUiEntry>()
    private val callbacks = LinkedHashMap<Call, Call.Callback>()
    private val mutableCalls = MutableStateFlow<List<CallUiEntry>>(emptyList())
    val calls: StateFlow<List<CallUiEntry>> = mutableCalls.asStateFlow()
    private val mutableAudio = MutableStateFlow(CallAudioUiState())
    val audio: StateFlow<CallAudioUiState> = mutableAudio.asStateFlow()

    @Volatile private var service: MoogInCallService? = null
    @Volatile var isMuted: Boolean = false
        private set
    @Volatile var isSpeakerOn: Boolean = false
        private set

    @Synchronized
    fun attach(owner: MoogInCallService, call: Call) {
        service = owner
        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) = update(call)
            override fun onDetailsChanged(call: Call, details: Call.Details) = update(call)
            override fun onParentChanged(call: Call, parent: Call?) = update(call)
            override fun onChildrenChanged(call: Call, children: MutableList<Call>) = update(call)
        }
        callbacks[call] = callback
        call.registerCallback(callback)
        update(call)
    }

    @Synchronized
    fun update(call: Call) {
        val handle = call.details.handle?.schemeSpecificPart.orEmpty()
        entries[call] = CallUiEntry(
            call = call,
            number = handle.ifBlank { "Private number" },
            state = call.state,
            canHold = call.details.callCapabilities and Call.Details.CAPABILITY_HOLD != 0
        )
        publish()
        service?.refreshCallNotification()
    }

    @Synchronized
    fun detach(call: Call) {
        callbacks.remove(call)?.let { call.unregisterCallback(it) }
        entries.remove(call)
        publish()
        if (entries.isEmpty()) {
            service?.clearCallNotification()
            service = null
            isMuted = false
            isSpeakerOn = false
            mutableAudio.value = CallAudioUiState()
        } else {
            service?.refreshCallNotification()
        }
    }

    @Synchronized
    private fun publish() {
        mutableCalls.value = entries.values.toList()
    }

    fun primaryCall(): CallUiEntry? {
        val snapshot = mutableCalls.value
        return snapshot.firstOrNull { it.state == Call.STATE_RINGING }
            ?: snapshot.firstOrNull { it.state == Call.STATE_ACTIVE || it.state == Call.STATE_DIALING || it.state == Call.STATE_CONNECTING }
            ?: snapshot.firstOrNull()
    }

    fun answer(call: Call) {
        call.answer(call.details.videoState)
    }

    fun reject(call: Call) {
        call.reject(false, null)
    }

    fun disconnect(call: Call) {
        call.disconnect()
    }

    fun toggleHold(call: Call) {
        if (call.state == Call.STATE_HOLDING) call.unhold() else call.hold()
    }

    fun toggleMute() {
        isMuted = !isMuted
        service?.setMuted(isMuted)
        mutableAudio.value = CallAudioUiState(isMuted, isSpeakerOn)
    }

    fun toggleSpeaker() {
        val nextRoute = if (isSpeakerOn) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER
        service?.setAudioRoute(nextRoute)
        isSpeakerOn = !isSpeakerOn
        mutableAudio.value = CallAudioUiState(isMuted, isSpeakerOn)
    }

    fun updateAudioState(state: CallAudioState?) {
        if (state != null) {
            isMuted = state.isMuted
            isSpeakerOn = state.route == CallAudioState.ROUTE_SPEAKER
            mutableAudio.value = CallAudioUiState(isMuted, isSpeakerOn)
        }
    }
}
