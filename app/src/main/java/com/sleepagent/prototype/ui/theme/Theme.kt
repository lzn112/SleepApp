package com.sleepagent.prototype.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Sleep-first night palette. The app defaults to this tone so it stays calm
// and readable even when the device system theme is light.
val DeepBlue80 = Color(0xFFB7C9FF)
val LightBlue80 = Color(0xFF8FE7E1)
val RoyalBlue80 = Color(0xFFD8C4FF)

val DeepBlue40 = Color(0xFF445DA8)
val LightBlue40 = Color(0xFF237B81)
val RoyalBlue40 = Color(0xFF6B55A8)

val SleepBackgroundDark = Color(0xFF050B18)
val SleepBackgroundLight = Color(0xFFEFF3FA)

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
    onPrimaryContainer = ColorTokens.DarkOnPrimaryContainer,
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
    val DarkSurface = Color(0xFF0B1424)
    val DarkSurfaceLow = Color(0xFF101B31)
    val DarkSurfaceMid = Color(0xFF1A2742)
    val DarkOnPrimary = Color(0xFF07111F)
    val DarkOnSecondary = Color(0xFF062322)
    val DarkOnTertiary = Color(0xFF171021)
    val DarkOnBackground = Color(0xFFF2F6FF)
    val DarkOnSurface = Color(0xFFF8FAFF)
    val DarkOnSurfaceVariant = Color(0xFFB9C5D9)
    val DarkPrimaryContainer = Color(0xFF23376A)
    val DarkOnPrimaryContainer = Color(0xFFE4EAFF)

    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceLow = Color(0xFFF8FAFF)
    val LightSurfaceMid = Color(0xFFE5EBF6)
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightOnSecondary = Color(0xFFFFFFFF)
    val LightOnTertiary = Color(0xFFFFFFFF)
    val LightOnBackground = Color(0xFF172033)
    val LightOnSurface = Color(0xFF172033)
    val LightOnSurfaceVariant = Color(0xFF526073)
    val LightPrimaryContainer = Color(0xFFE3E8FF)
    val LightOnPrimaryContainer = Color(0xFF263A73)
}
