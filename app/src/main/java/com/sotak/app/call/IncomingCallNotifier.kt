package com.sotak.app.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sotak.app.MainActivity
import com.sotak.app.R
import com.sotak.app.service.SotakService

/**
 * Builds every notification the app shows.
 *
 * The incoming-call notification is high importance and carries a full-screen
 * intent, so the call UI comes straight up over the lock screen -- exactly like a
 * normal phone call. The app plays the ringtone itself, so the channel itself is
 * silent (no double ringing).
 */
class IncomingCallNotifier(context: Context) {

    companion object {
        const val CHANNEL_INCOMING = "incoming_calls"
        const val CHANNEL_SERVICE = "call_service"

        const val INCOMING_ID = 1001

        const val ACTION_ACCEPT = "com.sotak.app.action.ACCEPT_CALL"
        const val ACTION_DECLINE = "com.sotak.app.action.DECLINE_CALL"
        const val ACTION_STOP = "com.sotak.app.action.STOP_SERVICE"
    }

    private val appContext = context.applicationContext
    private val manager =
        appContext.getSystemService(NotificationManager::class.java)

    private val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val incoming = NotificationChannel(
            CHANNEL_INCOMING,
            "المكالمات الواردة",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "تنبيه عند وصول مكالمة صوتية عبر Wi-Fi Direct"
            setSound(null, null)      // the app rings on its own
            enableVibration(false)    // and vibrates on its own
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val service = NotificationChannel(
            CHANNEL_SERVICE,
            "حالة الاستقبال",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "يبقى التطبيق جاهزًا لاستقبال المكالمات"
            setShowBadge(false)
        }

        manager?.createNotificationChannel(incoming)
        manager?.createNotificationChannel(service)
    }

    fun showIncoming(peerName: String) {
        val contentIntent = PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            piFlags,
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_call_notification)
            .setContentTitle("مكالمة واردة")
            .setContentText("$peerName يتصل بك")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(contentIntent, true)
            .setContentIntent(contentIntent)
            .addAction(0, "رد", servicePendingIntent(ACTION_ACCEPT, 1))
            .addAction(0, "رفض", servicePendingIntent(ACTION_DECLINE, 2))
            .build()

        runCatching { manager?.notify(INCOMING_ID, notification) }
    }

    fun cancelIncoming() {
        runCatching { manager?.cancel(INCOMING_ID) }
    }

    fun buildServiceNotification(title: String, text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            appContext,
            3,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            piFlags,
        )

        return NotificationCompat.Builder(appContext, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_call_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(contentIntent)
            .addAction(0, "إيقاف", servicePendingIntent(ACTION_STOP, 4))
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            appContext,
            requestCode,
            Intent(appContext, SotakService::class.java).setAction(action),
            piFlags,
        )
}
