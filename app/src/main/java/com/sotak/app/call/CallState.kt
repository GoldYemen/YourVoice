package com.sotak.app.call

/** High-level state machine that drives the whole user interface. */
sealed interface CallState {

    /** Nothing is happening yet: the user is on the home screen. */
    data object Idle : CallState

    /**
     * A call is ringing.
     *
     * @param isCaller true when *we* dialled and are waiting for an answer,
     *                 false when the phone is ringing because of an incoming call.
     */
    data class Ringing(val peerName: String, val isCaller: Boolean) : CallState

    /** Handshake in progress (group forming, or waiting for audio to come up). */
    data class Connecting(val peerName: String) : CallState

    /** Link is up and voice is flowing. */
    data class Active(
        val peerName: String,
        val isHost: Boolean,
        val startedAt: Long,
    ) : CallState

    data class Ended(val reason: String) : CallState
}
