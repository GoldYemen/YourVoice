package com.sotak.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SotakColorScheme = darkColorScheme(
    primary = SotakPrimary,
    onPrimary = SotakBackground,
    secondary = SotakAccent,
    onSecondary = SotakTextPrimary,
    background = SotakBackground,
    onBackground = SotakTextPrimary,
    surface = SotakSurface,
    onSurface = SotakTextPrimary,
    surfaceVariant = SotakSurfaceHigh,
    onSurfaceVariant = SotakTextMuted,
    error = SotakDanger,
)

@Composable
fun SotakTheme(
    // The app is intentionally dark-only: it is used in the dark, on a call.
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = SotakColorScheme,
        typography = SotakTypography,
        content = content,
    )
}
