package com.sotak.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sotak.app.ui.components.PulseRing
import com.sotak.app.ui.theme.SotakAccent
import com.sotak.app.ui.theme.SotakPrimary
import com.sotak.app.ui.theme.SotakSurface
import com.sotak.app.ui.theme.SotakSurfaceHigh
import com.sotak.app.ui.theme.SotakTextMuted
import com.sotak.app.ui.theme.SotakTextPrimary
import com.sotak.app.wifi.PeerDevice
import com.sotak.app.wifi.WifiDirectState

@Composable
fun HomeScreen(
    state: WifiDirectState,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onScan: () -> Unit,
    onCall: (PeerDevice) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))

        Text(
            text = "صوتك",
            style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
            color = SotakTextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "مكالمات مباشرة عبر Wi-Fi Direct — بدون إنترنت",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = SotakTextMuted,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(36.dp))

        StatusChip(state)

        Spacer(Modifier.height(32.dp))

        ScanButton(
            discovering = state.discovering,
            enabled = permissionsGranted,
            onClick = { if (permissionsGranted) onScan() else onRequestPermissions() },
        )

        Spacer(Modifier.height(28.dp))

        if (!permissionsGranted) {
            Text(
                text = "التطبيق يحتاج إذن الميكروفون والأجهزة القريبة ليعمل.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = SotakTextMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            PermissionButton(onRequestPermissions)
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = if (state.peers.isEmpty()) "لا توجد أجهزة بعد" else "الأجهزة القريبة (${state.peers.size})",
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            color = SotakTextPrimary,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.peers) { peer ->
                PeerCard(peer = peer, onCall = { onCall(peer) })
            }
        }
    }
}

@Composable
private fun StatusChip(state: WifiDirectState) {
    val (label, dotColor) = when {
        !state.supported -> "Wi-Fi Direct غير مدعوم" to SotakTextMuted
        !state.enabled -> "شغّل الواي فاي للبدء" to SotakTextMuted
        state.discovering -> "جارٍ البحث عن أجهزة…" to SotakPrimary
        else -> "جاهز — Wi-Fi Direct يعمل" to SotakPrimary
    }

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(SotakSurface)
            .border(1.dp, SotakSurfaceHigh, CircleShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Icon(Icons.Rounded.Wifi, contentDescription = null, tint = SotakTextMuted, modifier = Modifier.size(18.dp))
        Text(label, color = SotakTextMuted, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ScanButton(discovering: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        PulseRing(
            color = if (enabled) SotakPrimary else SotakAccent,
            size = 190.dp,
            active = discovering,
        )
        Surface(
            shape = CircleShape,
            color = androidx.compose.ui.graphics.Color.Transparent,
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            if (enabled) SotakPrimary else SotakAccent,
                            SotakAccent,
                        ),
                    ),
                )
                .clickable(onClick = onClick),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color(0xFF060A14),
                        modifier = Modifier.size(34.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (discovering) "يبحث…" else "ابحث",
                        color = androidx.compose.ui.graphics.Color(0xFF060A14),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionButton(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SotakAccent,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = "السماح بالإذن",
            color = SotakTextPrimary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PeerCard(peer: PeerDevice, onCall: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SotakSurface)
            .border(1.dp, SotakSurfaceHigh, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(SotakSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Wifi, contentDescription = null, tint = SotakPrimary)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = peer.displayName,
                color = SotakTextPrimary,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = peer.address,
                color = SotakTextMuted,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
        }
        Surface(
            shape = CircleShape,
            color = SotakPrimary,
            modifier = Modifier
                .size(52.dp)
                .clickable(onClick = onCall),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Phone,
                    contentDescription = "اتصال",
                    tint = androidx.compose.ui.graphics.Color(0xFF060A14),
                )
            }
        }
    }
}
