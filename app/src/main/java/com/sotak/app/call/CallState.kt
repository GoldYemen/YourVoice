package com.sotak.app.call

/** High-level state machine that drives the whole user interface. */
sealed interface CallState {

    /** Nothing is happening yet: the user is on the home screen. */
    data object Idle : CallState

    /** Wi-Fi Direct handshake in progress. */
    data class Connecting(val peerName: String) : CallState

    /** Link is up and voice is flowing. */
    data class Active(
        val peerName: String,
        val isHost: Boolean,
        val startedAt: Long,
    ) : CallState

    data class Ended(val reason: String) : CallState
}
