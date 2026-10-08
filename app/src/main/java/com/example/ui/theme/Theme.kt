package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Emerald400,
    onPrimary = Slate900,
    primaryContainer = Slate800,
    onPrimaryContainer = Emerald100,
    secondary = Cyan400,
    onSecondary = Slate900,
    surface = Slate900,
    onSurface = Slate50,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate200,
    background = Color(0xFF090D16),
    onBackground = Slate50,
    error = Rose500,
    outline = Slate700
)

private val LightColorScheme = lightColorScheme(
    primary = Emerald600,
    onPrimary = Color.White,
    primaryContainer = Emerald100,
    onPrimaryContainer = Emerald600,
    secondary = Cyan600,
    onSecondary = Color.White,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate700,
    background = Slate50,
    onBackground = Slate900,
    error = Rose500,
    outline = Slate200
)

@Composable
fun HomeAiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
