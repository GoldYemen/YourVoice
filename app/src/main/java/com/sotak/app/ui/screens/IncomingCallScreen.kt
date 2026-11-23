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
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sotak.app.ui.components.PulseRing
import com.sotak.app.ui.theme.SotakDanger
import com.sotak.app.ui.theme.SotakPrimary
import com.sotak.app.ui.theme.SotakSurface
import com.sotak.app.ui.theme.SotakSurfaceHigh
import com.sotak.app.ui.theme.SotakTextMuted
import com.sotak.app.ui.theme.SotakTextPrimary

/**
 * Full-screen incoming call UI with accept / decline.
 *
 * The activity is shown over the lock screen by the notification's full-screen
 * intent, so this is what the user sees when their phone rings.
 */
@Composable
fun IncomingCallScreen(
    peerName: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(96.dp))

        Box(contentAlignment = Alignment.Center) {
            PulseRing(color = SotakPrimary, size = 280.dp, active = true)
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
            text = "مكالمة واردة",
            style = MaterialTheme.typography.bodyLarge,
            color = SotakPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = peerName,
            style = MaterialTheme.typography.headlineMedium,
            color = SotakTextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "اتصال مباشر عبر Wi-Fi Direct · بدون إنترنت",
            style = MaterialTheme.typography.bodyMedium,
            color = SotakTextMuted,
        )

        Spacer(Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnswerButton(
                label = "رفض",
                color = SotakDanger,
                icon = { tint ->
                    Icon(Icons.Rounded.CallEnd, contentDescription = "رفض", tint = tint)
                },
                onClick = onDecline,
            )
            AnswerButton(
                label = "رد",
                color = SotakPrimary,
                icon = { tint ->
                    Icon(Icons.Rounded.Call, contentDescription = "رد", tint = tint)
                },
                onClick = onAccept,
            )
        }

        Spacer(Modifier.height(56.dp))
    }
}

@Composable
private fun AnswerButton(
    label: String,
    color: Color,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = color,
            modifier = Modifier
                .size(76.dp)
                .clickable(onClick = onClick),
        ) {
            Box(contentAlignment = Alignment.Center) {
                icon(Color(0xFF060A14))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = SotakTextPrimary,
        )
    }
}
