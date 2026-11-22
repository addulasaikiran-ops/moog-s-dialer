package com.moog.dialer.telecom

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.PhoneAccountHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CallAudioUiState(
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val availableRoutes: Int = CallAudioState.ROUTE_EARPIECE
)

data class CallUiEntry(
    val call: Call,
    val number: String,
    val state: Int,
    val canHold: Boolean,
    val simLabel: String
)

object CallSession {
    private val entries = LinkedHashMap<Call, CallUiEntry>()
    private val callbacks = LinkedHashMap<Call, Call.Callback>()
    private val mutableCalls = MutableStateFlow<List<CallUiEntry>>(emptyList())
    val calls: StateFlow<List<CallUiEntry>> = mutableCalls.asStateFlow()
    private val mutableAudio = MutableStateFlow(CallAudioUiState())
    val audio: StateFlow<CallAudioUiState> = mutableAudio.asStateFlow()

    @Volatile private var service: MoogInCallService? = null
    @Volatile private var currentAudioRoute: Int = CallAudioState.ROUTE_EARPIECE
    @Volatile private var availableAudioRoutes: Int = CallAudioState.ROUTE_EARPIECE
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
        val accountHandle = call.details.accountHandle
        val accountLabel = resolveAccountLabel(accountHandle)
        entries[call] = CallUiEntry(
            call = call,
            number = handle.ifBlank { "Private number" },
            state = call.state,
            canHold = call.details.callCapabilities and Call.Details.CAPABILITY_HOLD != 0,
            simLabel = accountLabel
        )
        publish()
        service?.refreshCallNotification()
    }

    private fun resolveAccountLabel(handle: PhoneAccountHandle?): String {
        if (handle == null) return "SIM not identified"
        return try {
            service?.getSystemService(android.telecom.TelecomManager::class.java)
                ?.getPhoneAccount(handle)
                ?.label
                ?.toString()
                ?.takeIf { it.isNotBlank() }
                ?: "SIM"
        } catch (_: SecurityException) {
            "SIM"
        } catch (_: RuntimeException) {
            "SIM"
        }
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
            currentAudioRoute = CallAudioState.ROUTE_EARPIECE
            availableAudioRoutes = CallAudioState.ROUTE_EARPIECE
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
        publishAudio()
    }

    fun toggleSpeaker() {
        val speakerAvailable = availableAudioRoutes and CallAudioState.ROUTE_SPEAKER != 0
        if (!speakerAvailable) return

        val nextRoute = if (currentAudioRoute == CallAudioState.ROUTE_SPEAKER) {
            when {
                availableAudioRoutes and CallAudioState.ROUTE_BLUETOOTH != 0 -> CallAudioState.ROUTE_BLUETOOTH
                availableAudioRoutes and CallAudioState.ROUTE_WIRED_HEADSET != 0 -> CallAudioState.ROUTE_WIRED_HEADSET
                availableAudioRoutes and CallAudioState.ROUTE_EARPIECE != 0 -> CallAudioState.ROUTE_EARPIECE
                else -> return
            }
        } else {
            CallAudioState.ROUTE_SPEAKER
        }
        service?.setAudioRoute(nextRoute)
    }

    fun updateAudioState(state: CallAudioState?) {
        if (state != null) {
            isMuted = state.isMuted
            currentAudioRoute = state.route
            availableAudioRoutes = state.supportedRouteMask
            isSpeakerOn = state.route == CallAudioState.ROUTE_SPEAKER
            publishAudio()
        }
    }

    private fun publishAudio() {
        mutableAudio.value = CallAudioUiState(
            isMuted = isMuted,
            isSpeakerOn = isSpeakerOn,
            availableRoutes = availableAudioRoutes
        )
    }
}
