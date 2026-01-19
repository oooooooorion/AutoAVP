package com.example.autoavp.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = LaPosteYellowDark,
    onPrimary = LaPosteBlueDark, // Texte bleu foncé sur jaune
    primaryContainer = LaPosteBlueDark,
    onPrimaryContainer = LaPosteYellow,
    secondary = LaPosteBlueLight,
    onSecondary = White,
    background = GreyDark,
    surface = Color(0xFF1E1E1E),
    onBackground = White,
    onSurface = White
)

private val LightColorScheme = lightColorScheme(
    primary = LaPosteYellow,
    onPrimary = LaPosteBlue, // Texte bleu La Poste sur fond Jaune
    primaryContainer = Color(0xFFFFEDA6), // Jaune très pâle
    onPrimaryContainer = LaPosteBlueDark,
    secondary = LaPosteBlue,
    onSecondary = White,
    background = White,
    surface = GreyLight,
    onBackground = Black,
    onSurface = Black,
    error = Color(0xFFBA1A1A)
)

@Composable
fun AutoAVPTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // On désactive les couleurs dynamiques (Android 12+) par défaut 
    // pour forcer la charte La Poste
    dynamicColor: Boolean = false, 
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}