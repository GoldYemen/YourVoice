package com.sotak.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sotak.app.call.CallState
import com.sotak.app.service.SotakService
import com.sotak.app.ui.components.SotakBackgroundBox
import com.sotak.app.ui.screens.CallScreen
import com.sotak.app.ui.screens.ConnectingScreen
import com.sotak.app.ui.screens.HomeScreen
import com.sotak.app.ui.screens.IncomingCallScreen
import com.sotak.app.ui.theme.SotakAccent
import com.sotak.app.ui.theme.SotakTextMuted
import com.sotak.app.ui.theme.SotakTextPrimary

/**
 * Root composable. Routes between home, the incoming-call screen and the live
 * call UI, and keeps runtime permissions and the Wi-Fi Direct receiver in sync
 * with the activity lifecycle.
 */
@Composable
fun SotakApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    var permissionsGranted by remember { mutableStateOf(hasCallPermissions(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionsGranted = hasCallPermissions(context)
        if (permissionsGranted) {
            viewModel.startScanning()
            startListeningService(context)
        }
    }

    // Keep the app reachable for incoming calls as soon as we are allowed to.
    LaunchedEffect(permissionsGranted) {
        if (permissionsGranted) startListeningService(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onStart()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val callState by viewModel.callState.collectAsStateWithLifecycle()
    val wifiState by viewModel.wifiState.collectAsStateWithLifecycle()
    val speakerOn by viewModel.speakerOn.collectAsStateWithLifecycle()
    val muted by viewModel.muted.collectAsStateWithLifecycle()

    SotakBackgroundBox {
        when (val state = callState) {
            is CallState.Idle -> HomeScreen(
                state = wifiState,
                permissionsGranted = permissionsGranted,
                onRequestPermissions = { permissionLauncher.launch(requiredPermissions()) },
                onScan = viewModel::startScanning,
                onCall = viewModel::call,
            )

            is CallState.Ringing -> if (state.isCaller) {
                ConnectingScreen(
                    peerName = state.peerName,
                    subtitle = "يرن… في انتظار الرد",
                    onCancel = viewModel::endCall,
                )
            } else {
                IncomingCallScreen(
                    peerName = state.peerName,
                    onAccept = viewModel::acceptCall,
                    onDecline = viewModel::declineCall,
                )
            }

            is CallState.Connecting -> ConnectingScreen(
                peerName = state.peerName,
                onCancel = viewModel::endCall,
            )

            is CallState.Active -> CallScreen(
                peerName = state.peerName,
                startedAt = state.startedAt,
                speakerOn = speakerOn,
                muted = muted,
                onToggleMute = viewModel::setMuted,
                onToggleSpeaker = viewModel::setSpeakerEnabled,
                onEnd = viewModel::endCall,
            )

            is CallState.Ended -> EndedScreen(
                message = state.reason,
                onDismiss = viewModel::endCall,
            )
        }
    }
}

@Composable
private fun EndedScreen(message: String, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "انتهت المكالمة",
            style = MaterialTheme.typography.headlineMedium,
            color = SotakTextPrimary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = SotakTextMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SotakAccent,
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onDismiss),
        ) {
            Text(
                text = "العودة",
                color = SotakTextPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp),
            )
        }
    }
}

private fun startListeningService(context: Context) {
    runCatching { SotakService.start(context) }
}

private fun requiredPermissions(): Array<String> {
    val permissions = mutableListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    return permissions.toTypedArray()
}

private fun hasCallPermissions(context: Context): Boolean =
    requiredPermissions().all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
