package com.melodia.player.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9A8FF),
    onPrimary = Color(0xFF240F80),
    primaryContainer = Color(0xFF3A27A6),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFFFFB59A),
    background = Color(0xFF121018),
    surface = Color(0xFF121018),
    surfaceVariant = Color(0xFF2A2633),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF4B2BD6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF16005E),
    secondary = Color(0xFFB4441C),
)

/** Na Androidzie 12+ kolory dopasowują się do tapety (Material You). */
@Composable
fun MelodiaTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
