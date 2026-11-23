package com.sotak.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.sotak.app.ui.theme.SotakAccent
import com.sotak.app.ui.theme.SotakBackground
import com.sotak.app.ui.theme.SotakPrimaryDim

/** Shared animated-feel gradient backdrop for every screen. */
@Composable
fun SotakBackgroundBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        SotakPrimaryDim.copy(alpha = 0.18f),
                        Color.Transparent,
                    ),
                    center = Offset(180f, 120f),
                    radius = 900f,
                ),
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        SotakAccent.copy(alpha = 0.16f),
                        Color.Transparent,
                    ),
                    center = Offset(900f, 1600f),
                    radius = 1100f,
                ),
            )
            .background(SotakBackground),
    ) {
        content()
    }
}
