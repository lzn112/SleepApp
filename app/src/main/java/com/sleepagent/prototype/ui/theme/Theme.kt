package com.sleepagent.prototype.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NightBlue80,
    secondary = MistBlue80,
    tertiary = RoseGlow80,
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
    primary = NightBlue40,
    secondary = MistBlue40,
    tertiary = RoseGlow40,
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
    darkTheme: Boolean = isSystemInDarkTheme(),
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
    val DarkSurface = androidx.compose.ui.graphics.Color(0xFF11192B)
    val DarkSurfaceLow = androidx.compose.ui.graphics.Color(0xFF182235)
    val DarkSurfaceMid = androidx.compose.ui.graphics.Color(0xFF1E2940)
    val DarkOnPrimary = androidx.compose.ui.graphics.Color(0xFF07111F)
    val DarkOnSecondary = androidx.compose.ui.graphics.Color(0xFF08111B)
    val DarkOnTertiary = androidx.compose.ui.graphics.Color(0xFF221018)
    val DarkOnBackground = androidx.compose.ui.graphics.Color(0xFFE8EDF8)
    val DarkOnSurface = androidx.compose.ui.graphics.Color(0xFFE7ECF7)
    val DarkOnSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFA7B2C9)
    val DarkPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF223459)
    val DarkOnPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFDCE5FF)

    val LightSurface = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    val LightSurfaceLow = androidx.compose.ui.graphics.Color(0xFFF0F4FB)
    val LightSurfaceMid = androidx.compose.ui.graphics.Color(0xFFE8EEF9)
    val LightOnPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    val LightOnSecondary = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    val LightOnTertiary = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    val LightOnBackground = androidx.compose.ui.graphics.Color(0xFF111827)
    val LightOnSurface = androidx.compose.ui.graphics.Color(0xFF121B2C)
    val LightOnSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF5A6781)
    val LightPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFDCE5FF)
    val LightOnPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF0D2148)
}
