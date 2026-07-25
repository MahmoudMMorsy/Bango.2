package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val BangoDarkColorScheme = darkColorScheme(
    primary = BangoGreen,
    onPrimary = Color.White,
    secondary = BangoAccentGreen,
    onSecondary = OnBackground,
    tertiary = BangoTeal,
    background = BackgroundDark,
    onBackground = OnBackground,
    surface = SurfaceDark,
    onSurface = OnSurface,
    surfaceContainer = SurfaceCard
)

private val BangoLightColorScheme = lightColorScheme(
    primary = BangoGreen,
    onPrimary = Color.White,
    secondary = BangoAccentGreen,
    onSecondary = BackgroundDark,
    tertiary = BangoTeal,
    background = OnBackground,
    onBackground = BackgroundDark,
    surface = Color.White,
    onSurface = SurfaceDark
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Preserve dark theme for premium Bango Look
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) BangoDarkColorScheme else BangoLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
