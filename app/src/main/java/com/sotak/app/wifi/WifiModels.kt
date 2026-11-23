package com.sotak.app.wifi

/** A discoverable peer on the Wi-Fi Direct network. */
data class PeerDevice(
    val name: String,
    val address: String,
    val status: Int,
) {
    val displayName: String get() = name.ifBlank { "جهاز قريب" }
}

/** Snapshot of the Wi-Fi Direct subsystem, surfaced to the UI. */
data class WifiDirectState(
    val supported: Boolean = true,
    val enabled: Boolean = false,
    val discovering: Boolean = false,
    val groupFormed: Boolean = false,
    val isGroupOwner: Boolean = false,
    val hostAddress: String? = null,
    val peers: List<PeerDevice> = emptyList(),
)
