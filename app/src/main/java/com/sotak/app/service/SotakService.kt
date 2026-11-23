package com.sotak.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sotak.app.SotakApplication
import com.sotak.app.call.CallCoordinator
import com.sotak.app.call.CallState
import com.sotak.app.call.IncomingCallNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the app alive and reachable so an incoming Wi-Fi Direct call can ring at
 * any moment -- even while the user is in another app.
 *
 * The notification doubles as the call UI entry point and as the accept/decline
 * remote control when the screen is locked.
 */
class SotakService : Service() {

    companion object {
        const val ACTION_START = "com.sotak.app.action.START_LISTENING"
        private const val FOREGROUND_ID = 1000

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, SotakService::class.java).setAction(ACTION_START),
            )
        }
    }

    private lateinit var coordinator: CallCoordinator
    private lateinit var notifier: IncomingCallNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        coordinator = (application as SotakApplication).coordinator
        notifier = IncomingCallNotifier(this)
        notifier.ensureChannels()
        coordinator.startListening()

        scope.launch {
            coordinator.callState.collect { state -> promoteToForeground(state) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Never leave the system waiting for a foreground notification.
        promoteToForeground(coordinator.callState.value)

        when (intent?.action) {
            IncomingCallNotifier.ACTION_ACCEPT -> coordinator.acceptCall()
            IncomingCallNotifier.ACTION_DECLINE -> coordinator.declineCall()
            IncomingCallNotifier.ACTION_STOP -> {
                coordinator.endCall()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun promoteToForeground(state: CallState) {
        val notification = when (state) {
            is CallState.Active -> notifier.buildServiceNotification(
                title = "مكالمة جارية",
                text = "${state.peerName} — اضغط للعودة إلى المكالمة",
            )

            is CallState.Ringing -> if (state.isCaller) {
                notifier.buildServiceNotification(
                    title = "يرن…",
                    text = "في انتظار رد ${state.peerName}",
                )
            } else {
                notifier.buildServiceNotification(
                    title = "مكالمة واردة",
                    text = "${state.peerName} يتصل بك",
                )
            }

            is CallState.Connecting -> notifier.buildServiceNotification(
                title = "جارٍ الاتصال",
                text = state.peerName,
            )

            else -> notifier.buildServiceNotification(
                title = "صوتك جاهز",
                text = "في انتظار مكالمات Wi-Fi Direct الواردة",
            )
        }

        // While a call is live we are capturing audio, so declare the matching
        // foreground-service type; otherwise a plain persistent service is enough.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (state is CallState.Active) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
        } else {
            0
        }

        runCatching {
            ServiceCompat.startForeground(this, FOREGROUND_ID, notification, type)
        }.onFailure {
            // Some OEM builds reject the microphone type when the permission state is
            // unusual; fall back to a plain persistent notification.
            runCatching { ServiceCompat.startForeground(this, FOREGROUND_ID, notification, 0) }
        }
    }
}
