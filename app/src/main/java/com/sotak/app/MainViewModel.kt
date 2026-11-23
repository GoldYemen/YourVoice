package com.sotak.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.sotak.app.wifi.PeerDevice

/**
 * Thin adapter between the UI and the application-scoped [com.sotak.app.call.CallCoordinator].
 * All the real work happens in the coordinator; the UI only observes and asks.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val coordinator = (application as SotakApplication).coordinator

    val wifiState = coordinator.wifiState
    val callState = coordinator.callState
    val speakerOn = coordinator.speakerOn
    val muted = coordinator.muted

    /** Begin watching for incoming calls. Safe to call on every start. */
    fun onStart() = coordinator.startListening()

    fun startScanning() = coordinator.beginScan()

    fun call(peer: PeerDevice) = coordinator.placeCall(peer)

    fun acceptCall() = coordinator.acceptCall()

    fun declineCall() = coordinator.declineCall()

    fun endCall() = coordinator.endCall()

    fun setMuted(value: Boolean) = coordinator.setMuted(value)

    fun setSpeakerEnabled(enabled: Boolean) = coordinator.setSpeakerEnabled(enabled)
}
