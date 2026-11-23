package com.sotak.app.call

import android.content.Context
import android.media.AudioManager
import android.net.wifi.p2p.WifiP2pInfo
import android.os.Build
import android.util.Log
import com.sotak.app.net.CallChannel
import com.sotak.app.wifi.PeerDevice
import com.sotak.app.wifi.WifiDirectManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The brain of the app: owns Wi-Fi Direct, the signalling channel, the audio
 * session, the ringer and the notifications.
 *
 * It is application-scoped so a call survives the UI going away -- the foreground
 * service keeps it alive, and the activity simply mirrors its state.
 */
class CallCoordinator(context: Context) {

    private companion object {
        const val TAG = "SotakCoordinator"
    }

    private val appContext = context.applicationContext

    private val wifi = WifiDirectManager(appContext)
    private val ringer = Ringer(appContext)
    private val notifier = IncomingCallNotifier(appContext)
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val wifiState = wifi.state

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _speakerOn = MutableStateFlow(false)
    val speakerOn: StateFlow<Boolean> = _speakerOn.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private var channel: CallChannel? = null
    private var session: CallSession? = null

    /** True when *this* device dialled, false when it is being called. */
    private var outgoing = false
    private var peerName = "جهاز قريب"
    private var listening = false

    /** True when *we* refused the incoming call, so we return straight home. */
    private var selfDeclined = false

    init {
        notifier.ensureChannels()
        wifi.onGroupFormed = ::onGroupFormed
        wifi.onGroupLost = ::onGroupLost
    }

    // ------------------------------------------------------------------ public

    /** Start watching for incoming calls. Safe to call repeatedly. */
    fun startListening() {
        if (listening) return
        listening = true
        wifi.register()
    }

    fun beginScan() = wifi.discoverPeers()

    fun placeCall(peer: PeerDevice) {
        outgoing = true
        peerName = peer.displayName
        _callState.value = CallState.Connecting(peerName)
        wifi.stopDiscovery()
        wifi.connect(peer.address)
    }

    fun acceptCall() {
        channel?.accept()
    }

    fun declineCall() {
        selfDeclined = true
        channel?.decline()
    }

    fun endCall() {
        _callState.value = CallState.Idle
        teardown(disconnectGroup = true)
    }

    fun setMuted(value: Boolean) {
        _muted.value = value
        session?.setMuted(value)
    }

    fun setSpeakerEnabled(enabled: Boolean) {
        _speakerOn.value = enabled
        runCatching { audioManager.isSpeakerphoneOn = enabled }
    }

    // ----------------------------------------------------------------- internal

    private fun onGroupFormed(info: WifiP2pInfo) {
        if (!info.groupFormed) return

        // Exactly one side is the group owner; that side listens on TCP.
        val isHost = info.isGroupOwner
        val host = info.groupOwnerAddress?.hostAddress

        channel?.close()
        val newChannel = CallChannel(
            isHost = isHost,
            hostAddress = host,
            isCaller = outgoing,
            localName = deviceName(),
        )
        channel = newChannel

        if (outgoing) {
            _callState.value = CallState.Ringing(peerName, isCaller = true)
        }

        newChannel.open(object : CallChannel.Listener {
            override fun onIncomingCall(incomingPeerName: String) {
                peerName = incomingPeerName
                _callState.value = CallState.Ringing(peerName, isCaller = false)
                ringer.start()
                notifier.showIncoming(peerName)
            }

            override fun onAccepted(acceptedPeerName: String) {
                if (acceptedPeerName.isNotBlank()) peerName = acceptedPeerName
                quietAlerts()
                _callState.value = CallState.Connecting(peerName)
            }

            override fun onDeclined() {
                quietAlerts()
                abandon(newChannel)
                _callState.value = if (selfDeclined) {
                    selfDeclined = false
                    CallState.Idle
                } else {
                    CallState.Ended("لم يتم الرد")
                }
            }

            override fun onReady() {
                quietAlerts()
                session?.stop()
                val newSession = CallSession(newChannel)
                newSession.setMuted(_muted.value)
                newSession.start()
                session = newSession

                applyAudioMode(true)
                _callState.value = CallState.Active(
                    peerName = peerName,
                    isHost = isHost,
                    startedAt = System.currentTimeMillis(),
                )
            }

            override fun onError(message: String) {
                Log.w(TAG, "call failed: $message")
                val wasInCall = isBusy()
                quietAlerts()
                abandon(newChannel)
                // A stray group forming while idle is not a call: reset silently.
                _callState.value = if (wasInCall) CallState.Ended(message) else CallState.Idle
            }
        })
    }

    /** The group dissolved -- the far side hung up, or the link dropped. */
    private fun onGroupLost() {
        if (!isBusy()) return
        quietAlerts()
        teardown(disconnectGroup = false)
        _callState.value = CallState.Ended("انتهت المكالمة")
    }

    private fun isBusy(): Boolean = when (_callState.value) {
        is CallState.Ringing, is CallState.Connecting, is CallState.Active -> true
        else -> false
    }

    private fun quietAlerts() {
        ringer.stop()
        notifier.cancelIncoming()
    }

    private fun abandon(dead: CallChannel) {
        session?.stop()
        session = null
        dead.close()
        if (channel === dead) channel = null
        applyAudioMode(false)
        runCatching { wifi.disconnect() }
    }

    private fun teardown(disconnectGroup: Boolean) {
        quietAlerts()
        session?.stop()
        session = null
        channel?.close()
        channel = null
        outgoing = false
        selfDeclined = false
        _muted.value = false
        _speakerOn.value = false
        applyAudioMode(false)
        if (disconnectGroup) runCatching { wifi.disconnect() }
    }

    private fun applyAudioMode(inCall: Boolean) {
        runCatching {
            audioManager.mode =
                if (inCall) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_NORMAL
            // Start on the earpiece unless the user already picked the speaker.
            audioManager.isSpeakerphoneOn = inCall && _speakerOn.value
        }
    }

    private fun deviceName(): String =
        Build.MODEL?.takeIf { it.isNotBlank() } ?: "جهاز"
}
