package com.sotak.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sotak.app.ui.components.PulseRing
import com.sotak.app.ui.theme.SotakAccent
import com.sotak.app.ui.theme.SotakDanger
import com.sotak.app.ui.theme.SotakPrimary
import com.sotak.app.ui.theme.SotakSurface
import com.sotak.app.ui.theme.SotakSurfaceHigh
import com.sotak.app.ui.theme.SotakTextMuted
import com.sotak.app.ui.theme.SotakTextPrimary
import kotlinx.coroutines.delay

@Composable
fun CallScreen(
    peerName: String,
    startedAt: Long,
    speakerOn: Boolean,
    muted: Boolean,
    onToggleMute: (Boolean) -> Unit,
    onToggleSpeaker: (Boolean) -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(startedAt) {
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000).coerceAtLeast(0)
            delay(1000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(96.dp))

        Box(contentAlignment = Alignment.Center) {
            PulseRing(color = SotakPrimary, size = 260.dp, active = true)
            Surface(
                shape = CircleShape,
                color = SotakSurface,
                modifier = Modifier
                    .size(150.dp)
                    .border(1.dp, SotakSurfaceHigh, CircleShape),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = peerName.take(1).uppercase(),
                        style = MaterialTheme.typography.displaySmall,
                        color = SotakPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = peerName,
            style = MaterialTheme.typography.headlineMedium,
            color = SotakTextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = formatDuration(elapsedSeconds),
            style = MaterialTheme.typography.bodyLarge,
            color = SotakTextMuted,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "مكالمة مباشرة · بدون إنترنت",
            style = MaterialTheme.typography.bodyMedium,
            color = SotakTextMuted,
        )

        Spacer(Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(
                active = muted,
                activeColor = SotakAccent,
                onClick = { onToggleMute(!muted) },
            ) {
                Icon(
                    imageVector = if (muted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    contentDescription = "كتم",
                    tint = if (muted) SotakTextPrimary else SotakTextMuted,
                )
            }

            Surface(
                shape = CircleShape,
                color = SotakDanger,
                modifier = Modifier
                    .size(76.dp)
                    .clickable(onClick = onEnd),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.CallEnd,
                        contentDescription = "إنهاء المكالمة",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            RoundButton(
                active = speakerOn,
                activeColor = SotakPrimary,
                onClick = { onToggleSpeaker(!speakerOn) },
            ) {
                Icon(
                    imageVector = Icons.Rounded.VolumeUp,
                    contentDescription = "مكبر الصوت",
                    tint = if (speakerOn) Color(0xFF060A14) else SotakTextMuted,
                )
            }
        }

        Spacer(Modifier.height(56.dp))
    }
}

@Composable
private fun RoundButton(
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = if (active) activeColor else SotakSurface,
        modifier = Modifier
            .size(64.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
fun ConnectingScreen(peerName: String, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PulseRing(color = SotakAccent, size = 240.dp, active = true)
        Spacer(Modifier.height(24.dp))
        Text(
            text = "جارٍ الاتصال بـ $peerName",
            style = MaterialTheme.typography.titleLarge,
            color = SotakTextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "يتم إنشاء اتصال Wi-Fi Direct مباشر…",
            style = MaterialTheme.typography.bodyMedium,
            color = SotakTextMuted,
        )
        Spacer(Modifier.height(40.dp))
        Surface(
            shape = CircleShape,
            color = SotakDanger,
            modifier = Modifier
                .size(72.dp)
                .clickable(onClick = onCancel),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.CallEnd, contentDescription = "إلغاء", tint = Color.White)
            }
        }
    }
}
