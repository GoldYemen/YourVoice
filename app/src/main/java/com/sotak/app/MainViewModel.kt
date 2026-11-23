package com.sotak.app

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.net.wifi.p2p.WifiP2pInfo
import androidx.lifecycle.AndroidViewModel
import com.sotak.app.call.CallSession
import com.sotak.app.call.CallState
import com.sotak.app.wifi.PeerDevice
import com.sotak.app.wifi.WifiDirectManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the app: owns the Wi-Fi Direct manager and the
 * active call session, and exposes everything the UI needs as flows.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val wifi = WifiDirectManager(application)
    val wifiState = wifi.state

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _speakerOn = MutableStateFlow(false)
    val speakerOn: StateFlow<Boolean> = _speakerOn.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val audioManager =
        application.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var session: CallSession? = null
    private var pendingPeerName: String = "جهاز قريب"

    init {
        wifi.onGroupFormed = ::onGroupFormed
    }

    fun onStart() = wifi.register()

    fun onStop() = wifi.unregister()

    fun startScanning() = wifi.discoverPeers()

    fun call(peer: PeerDevice) {
        pendingPeerName = peer.displayName
        _callState.value = CallState.Connecting(pendingPeerName)
        wifi.stopDiscovery()
        wifi.connect(peer.address)
    }

    fun endCall() {
        session?.stop()
        session = null
        _muted.value = false
        _speakerOn.value = false
        applyAudioMode(false)
        wifi.disconnect()
        _callState.value = CallState.Idle
    }

    fun setMuted(value: Boolean) {
        _muted.value = value
        session?.setMuted(value)
    }

    fun setSpeakerEnabled(enabled: Boolean) {
        _speakerOn.value = enabled
        runCatching { audioManager.isSpeakerphoneOn = enabled }
    }

    private fun onGroupFormed(info: WifiP2pInfo) {
        if (!info.groupFormed) return
        // The group owner hosts; the other side connects to it.
        val isHost = info.isGroupOwner
        val host = info.groupOwnerAddress?.hostAddress

        val newSession = CallSession(isHost = isHost, hostAddress = host)
        newSession.setMuted(_muted.value)
        newSession.start(
            onReady = {
                applyAudioMode(true)
                _callState.value = CallState.Active(
                    peerName = pendingPeerName,
                    isHost = isHost,
                    startedAt = System.currentTimeMillis(),
                )
            },
            onError = { message -> _callState.value = CallState.Ended(message) },
        )
        session = newSession
    }

    private fun applyAudioMode(inCall: Boolean) {
        runCatching {
            audioManager.mode =
                if (inCall) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_NORMAL
            // Start on the earpiece unless the user already picked the speaker.
            audioManager.isSpeakerphoneOn = inCall && _speakerOn.value
        }
    }

    override fun onCleared() {
        super.onCleared()
        endCall()
    }
}
