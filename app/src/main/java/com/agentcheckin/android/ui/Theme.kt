package com.agentcheckin.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val WorkBuddyBlue = Color(0xFF2F6BFF)
val QoderPurple = Color(0xFF7B5CFF)
val SuccessGreen = Color(0xFF0E7A52)
val WarningAmber = Color(0xFF9A5B00)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3157E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E8FF),
    onPrimaryContainer = Color(0xFF152A66),
    secondary = Color(0xFF5B4BD6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FF),
    onSecondaryContainer = Color(0xFF231A5C),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF151A24),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF151A24),
    surfaceVariant = Color(0xFFF0F2F8),
    onSurfaceVariant = Color(0xFF5C6373),
    outline = Color(0xFFC9CFDD),
    outlineVariant = Color(0xFFE3E7F0),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB1FF),
    onPrimary = Color(0xFF0A1F66),
    primaryContainer = Color(0xFF233B8F),
    onPrimaryContainer = Color(0xFFDDE3FF),
    secondary = Color(0xFFC4BBFF),
    onSecondary = Color(0xFF2C2170),
    secondaryContainer = Color(0xFF3E348A),
    onSecondaryContainer = Color(0xFFE8E3FF),
    background = Color(0xFF0B0F17),
    onBackground = Color(0xFFE8EAF2),
    surface = Color(0xFF141A26),
    onSurface = Color(0xFFE8EAF2),
    surfaceVariant = Color(0xFF1C2433),
    onSurfaceVariant = Color(0xFFA8B0C2),
    outline = Color(0xFF3A4458),
    outlineVariant = Color(0xFF242D3D),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun AgentCheckinTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
