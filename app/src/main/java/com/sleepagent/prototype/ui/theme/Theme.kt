package com.sleepagent.prototype.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = DeepBlue80,
    secondary = LightBlue80,
    tertiary = RoyalBlue80,
    background = SleepBackgroundDark,
    surface = ColorTokens.DarkSurface,
    surfaceContainerLow = ColorTokens.DarkSurfaceLow,
    surfaceContainer = ColorTokens.DarkSurfaceMid,
    onPrimary = ColorTokens.DarkOnPrimary,
    onSecondary = ColorTokens.DarkOnSecondary,
    onTertiary = ColorTokens.DarkOnTertiary,
    onBackground = ColorTokens.DarkOnBackground,
    onSurface = ColorTokens.DarkOnSurface,
    onSurfaceVariant = ColorTokens.DarkOnSurfaceVariant,
    primaryContainer = ColorTokens.DarkPrimaryContainer,
    onPrimaryContainer = ColorTokens.DarkOnPrimaryContainer
)

private val LightColorScheme = lightColorScheme(
    primary = DeepBlue40,
    secondary = LightBlue40,
    tertiary = RoyalBlue40,
    background = SleepBackgroundLight,
    surface = ColorTokens.LightSurface,
    surfaceContainerLow = ColorTokens.LightSurfaceLow,
    surfaceContainer = ColorTokens.LightSurfaceMid,
    onPrimary = ColorTokens.LightOnPrimary,
    onSecondary = ColorTokens.LightOnSecondary,
    onTertiary = ColorTokens.LightOnTertiary,
    onBackground = ColorTokens.LightOnBackground,
    onSurface = ColorTokens.LightOnSurface,
    onSurfaceVariant = ColorTokens.LightOnSurfaceVariant,
    primaryContainer = ColorTokens.LightPrimaryContainer,
    onPrimaryContainer = ColorTokens.LightOnPrimaryContainer
)

@Composable
fun SleepAgentPrototypeTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (dynamicColor) {
        if (darkTheme) DarkColorScheme else LightColorScheme
    } else if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

private object ColorTokens {
    val DarkSurface = androidx.compose.ui.graphics.Color(0xFF0B1424)
    val DarkSurfaceLow = androidx.compose.ui.graphics.Color(0xFF101B31)
    val DarkSurfaceMid = androidx.compose.ui.graphics.Color(0xFF1A2742)
    val DarkOnPrimary = androidx.compose.ui.graphics.Color(0xFF07111F)
    val DarkOnSecondary = androidx.compose.ui.graphics.Color(0xFF062322)
    val DarkOnTertiary = androidx.compose.ui.graphics.Color(0xFF171021)
    val DarkOnBackground = androidx.compose.ui.graphics.Color(0xFFF2F6FF)
    val DarkOnSurface = androidx.compose.ui.graphics.Color(0xFFF8FAFF)
    val DarkOnSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFB9C5D9)
    val DarkPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF23376A)
    val DarkOnPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFE4EAFF)

    val LightSurface = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    val LightSurfaceLow = androidx.compose.ui.graphics.Color(0xFFF8FAFF)
    val LightSurfaceMid = androidx.compose.ui.graphics.Color(0xFFE5EBF6)
    val LightOnPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    val LightOnSecondary = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    val LightOnTertiary = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    val LightOnBackground = androidx.compose.ui.graphics.Color(0xFF172033)
    val LightOnSurface = androidx.compose.ui.graphics.Color(0xFF172033)
    val LightOnSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF526073)
    val LightPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFE3E8FF)
    val LightOnPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF263A73)
}
