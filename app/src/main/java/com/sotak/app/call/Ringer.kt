package com.sotak.app.call

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

/**
 * Plays the device's own ringtone, looping, and vibrates in step with it.
 *
 * Uses the system ringtone, so the app ships no audio assets at all.
 */
class Ringer(context: Context) {

    private companion object {
        const val TAG = "SotakRinger"
        val VIBRATION_PATTERN = longArrayOf(0L, 800L, 600L, 800L, 600L)
    }

    private val appContext = context.applicationContext

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    fun start() {
        stop()
        startRingtone()
        startVibration()
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun startRingtone() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return

        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(appContext, uri)
                isLooping = true
                prepare()
                start()
            }
        }.onFailure {
            Log.w(TAG, "ringtone unavailable", it)
            player = null
        }
    }

    private fun startVibration() {
        val device = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        vibrator = device
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                device.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0))
            } else {
                @Suppress("DEPRECATION")
                device.vibrate(VIBRATION_PATTERN, 0)
            }
        }.onFailure { Log.w(TAG, "vibration unavailable", it) }
    }
}
