package com.sotak.app.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.Channel
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thin, lifecycle-aware wrapper around the Android Wi-Fi Direct stack.
 *
 * It exposes discovery, connection and group information as a single
 * [StateFlow] that the UI observes, and notifies [onGroupFormed] once a direct
 * link between two devices is established.
 */
class WifiDirectManager(private val context: Context) {

    private companion object {
        const val TAG = "SotakWifiDirect"
    }

    private val manager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager

    private val channel: Channel? =
        manager?.initialize(context, Looper.getMainLooper(), null)

    private val _state = MutableStateFlow(WifiDirectState(supported = manager != null))
    val state: StateFlow<WifiDirectState> = _state.asStateFlow()

    /** Invoked once the group is formed and the direct link is usable. */
    var onGroupFormed: ((WifiP2pInfo) -> Unit)? = null

    /** Invoked when the group dissolves -- the far side hung up or the link dropped. */
    var onGroupLost: (() -> Unit)? = null

    /** Guards against double register/unregister of the broadcast receiver. */
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val enabled = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1) ==
                        WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    _state.value = _state.value.copy(enabled = enabled)
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeers()

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> requestConnectionInfo()

                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> Unit
            }
        }
    }

    private val filter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    fun register() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    fun unregister() {
        if (!receiverRegistered) return
        runCatching { context.unregisterReceiver(receiver) }
        receiverRegistered = false
    }

    @SuppressLint("MissingPermission")
    fun discoverPeers() {
        val c = channel ?: return
        _state.value = _state.value.copy(discovering = true)
        runCatching {
            manager?.discoverPeers(c, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "discovery started")
                }

                override fun onFailure(reason: Int) {
                    Log.w(TAG, "discovery failed: $reason")
                    _state.value = _state.value.copy(discovering = false)
                }
            })
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        val c = channel ?: return
        // The default WPS setup for Wi-Fi Direct is Push-Button (PBC), which is
        // exactly what a user-initiated pairing wants.
        val config = WifiP2pConfig().apply {
            deviceAddress = address
        }
        runCatching {
            manager?.connect(c, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "connect requested")
                }

                override fun onFailure(reason: Int) {
                    Log.w(TAG, "connect failed: $reason")
                }
            })
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestPeers() {
        val c = channel ?: return
        runCatching {
            manager?.requestPeers(c) { list ->
                val peers = list.map { device ->
                    PeerDevice(
                        name = device.deviceName ?: "",
                        address = device.deviceAddress ?: "",
                        status = device.status,
                    )
                }
                _state.value = _state.value.copy(peers = peers, discovering = false)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestConnectionInfo() {
        val c = channel ?: return
        runCatching {
            manager?.requestConnectionInfo(c) { info ->
                _state.value = _state.value.copy(
                    groupFormed = info.groupFormed,
                    isGroupOwner = info.isGroupOwner,
                    hostAddress = info.groupOwnerAddress?.hostAddress,
                )
                if (info.groupFormed) onGroupFormed?.invoke(info) else onGroupLost?.invoke()
            }
        }
    }

    fun disconnect() {
        val c = channel ?: return
        runCatching { manager?.removeGroup(c, null) }
        _state.value = _state.value.copy(
            groupFormed = false,
            isGroupOwner = false,
            hostAddress = null,
        )
    }

    fun stopDiscovery() {
        channel?.let { c -> runCatching { manager?.stopPeerDiscovery(c, null) } }
        _state.value = _state.value.copy(discovering = false)
    }
}
