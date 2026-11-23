package com.sotak.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A soft, continuously expanding ring used behind the main call button.
 * Pure Compose animation -- no assets, no extra size.
 */
@Composable
fun PulseRing(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    active: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse-progress",
    )

    val scale = if (active) 0.85f + progress * 0.4f else 0.9f
    val alpha = if (active) (1f - progress).coerceIn(0f, 1f) * 0.5f else 0.25f

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .alpha(alpha)
            .background(
                brush = Brush.radialGradient(listOf(color, Color.Transparent)),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {}
}
