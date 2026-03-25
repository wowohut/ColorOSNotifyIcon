package com.fankes.coloros.notify.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun OStatusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val lightColorScheme = lightColorScheme(
        primary = Color(0xFF0061A4),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD1E4FF),
        onPrimaryContainer = Color(0xFF001D36),
        secondary = Color(0xFF535F70),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFEAEAEA),
        onSecondaryContainer = Color(0xFF121212),
        background = Color(0xFFF2F2F7),
        onBackground = Color(0xFF191C20),
        surface = Color(0xFFF2F2F7),
        onSurface = Color(0xFF191C20),
        surfaceVariant = Color(0xFFE5E5EA),
        onSurfaceVariant = Color(0xFF43474E),
        surfaceContainer = Color(0xFFFFFFFF),
        surfaceContainerHigh = Color(0xFFE5E5EA),
    )

    val darkColorScheme = darkColorScheme(
        primary = Color(0xFF9ECAFF),
        onPrimary = Color(0xFF003258),
        primaryContainer = Color(0xFF00497D),
        onPrimaryContainer = Color(0xFFD1E4FF),
        secondary = Color(0xFFBBC7DB),
        onSecondary = Color(0xFF253140),
        secondaryContainer = Color(0xFF2C2C2E),
        onSecondaryContainer = Color(0xFFE5E5EA),
        background = Color(0xFF000000),
        onBackground = Color(0xFFE2E2E9),
        surface = Color(0xFF000000),
        onSurface = Color(0xFFE2E2E9),
        surfaceVariant = Color(0xFF2C2C2E),
        onSurfaceVariant = Color(0xFFC3C7CF),
        surfaceContainer = Color(0xFF1C1C1E),
        surfaceContainerHigh = Color(0xFF2C2C2E),
    )

    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme else lightColorScheme,
        typography = Typography(),
        content = content,
    )
}
