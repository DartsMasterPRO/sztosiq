package com.melodia.player.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import com.melodia.player.data.UiSettings

/** Gotowe motywy kolorystyczne. */
val THEME_PRESETS: List<Pair<String, Color>> = listOf(
    "Fiolet" to Color(0xFF6B4EFF),
    "Ocean" to Color(0xFF0E7CC9),
    "Turkus" to Color(0xFF00A3A3),
    "Las" to Color(0xFF2E9E5B),
    "Złoto" to Color(0xFFD9A400),
    "Zachód" to Color(0xFFFF6B3D),
    "Czerwień" to Color(0xFFD32F2F),
    "Róż" to Color(0xFFE0457B),
    "Lawenda" to Color(0xFF9C6ADE),
    "Grafit" to Color(0xFF6D7A8A),
)

/** Ustawienia dostępne w całym UI (styl list, ekranu odtwarzania itp.). */
val LocalUiSettings = staticCompositionLocalOf { UiSettings() }

private fun hsl(h: Float, s: Float, l: Float): Color =
    Color(ColorUtils.HSLToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))))

/** Buduje pełną paletę Material 3 z jednego koloru bazowego. */
fun schemeFromSeed(seed: Color, dark: Boolean): ColorScheme {
    val out = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), out)
    val h = out[0]
    val s = out[1].coerceIn(0.3f, 0.85f)
    return if (dark) {
        val primary = hsl(h, s, 0.74f)
        darkColorScheme(
            primary = primary,
            onPrimary = hsl(h, s, 0.15f),
            primaryContainer = hsl(h, s * 0.7f, 0.30f),
            onPrimaryContainer = hsl(h, s, 0.90f),
            inversePrimary = hsl(h, s, 0.40f),
            secondary = hsl(h + 25f, s * 0.55f, 0.76f),
            onSecondary = hsl(h + 25f, s * 0.5f, 0.15f),
            secondaryContainer = hsl(h + 25f, s * 0.4f, 0.27f),
            onSecondaryContainer = hsl(h + 25f, s * 0.5f, 0.90f),
            tertiary = hsl(h + 60f, s * 0.6f, 0.76f),
            onTertiary = hsl(h + 60f, s * 0.5f, 0.15f),
            tertiaryContainer = hsl(h + 60f, s * 0.45f, 0.28f),
            onTertiaryContainer = hsl(h + 60f, s * 0.5f, 0.90f),
            background = hsl(h, 0.18f, 0.07f),
            onBackground = hsl(h, 0.10f, 0.92f),
            surface = hsl(h, 0.18f, 0.07f),
            onSurface = hsl(h, 0.10f, 0.92f),
            surfaceVariant = hsl(h, 0.14f, 0.22f),
            onSurfaceVariant = hsl(h, 0.12f, 0.78f),
            surfaceTint = primary,
            inverseSurface = hsl(h, 0.10f, 0.90f),
            inverseOnSurface = hsl(h, 0.15f, 0.15f),
            outline = hsl(h, 0.08f, 0.55f),
            outlineVariant = hsl(h, 0.10f, 0.30f),
            surfaceBright = hsl(h, 0.14f, 0.24f),
            surfaceDim = hsl(h, 0.18f, 0.07f),
            surfaceContainerLowest = hsl(h, 0.18f, 0.05f),
            surfaceContainerLow = hsl(h, 0.16f, 0.10f),
            surfaceContainer = hsl(h, 0.16f, 0.12f),
            surfaceContainerHigh = hsl(h, 0.15f, 0.16f),
            surfaceContainerHighest = hsl(h, 0.14f, 0.20f),
        )
    } else {
        val primary = hsl(h, s, 0.40f)
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = hsl(h, s, 0.90f),
            onPrimaryContainer = hsl(h, s, 0.12f),
            inversePrimary = hsl(h, s, 0.78f),
            secondary = hsl(h + 25f, s * 0.45f, 0.40f),
            onSecondary = Color.White,
            secondaryContainer = hsl(h + 25f, s * 0.5f, 0.90f),
            onSecondaryContainer = hsl(h + 25f, s * 0.5f, 0.12f),
            tertiary = hsl(h + 60f, s * 0.5f, 0.40f),
            onTertiary = Color.White,
            tertiaryContainer = hsl(h + 60f, s * 0.5f, 0.90f),
            onTertiaryContainer = hsl(h + 60f, s * 0.5f, 0.12f),
            background = hsl(h, 0.30f, 0.98f),
            onBackground = hsl(h, 0.15f, 0.10f),
            surface = hsl(h, 0.30f, 0.98f),
            onSurface = hsl(h, 0.15f, 0.10f),
            surfaceVariant = hsl(h, 0.20f, 0.90f),
            onSurfaceVariant = hsl(h, 0.10f, 0.30f),
            surfaceTint = primary,
            inverseSurface = hsl(h, 0.10f, 0.18f),
            inverseOnSurface = hsl(h, 0.20f, 0.95f),
            outline = hsl(h, 0.08f, 0.50f),
            outlineVariant = hsl(h, 0.12f, 0.80f),
            surfaceBright = hsl(h, 0.30f, 0.98f),
            surfaceDim = hsl(h, 0.15f, 0.87f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = hsl(h, 0.30f, 0.96f),
            surfaceContainer = hsl(h, 0.28f, 0.94f),
            surfaceContainerHigh = hsl(h, 0.25f, 0.92f),
            surfaceContainerHighest = hsl(h, 0.22f, 0.90f),
        )
    }
}

@Composable
fun MelodiaTheme(ui: UiSettings, artColor: Color?, content: @Composable () -> Unit) {
    val dark = when (ui.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val ctx = LocalContext.current
    val presetColor = THEME_PRESETS.getOrElse(ui.themePreset) { THEME_PRESETS.first() }.second
    val seedTarget = if (ui.colorSource == 1 && artColor != null) artColor else presetColor
    val seed by animateColorAsState(seedTarget, tween(700), label = "seed")

    var scheme = if (ui.colorSource == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        schemeFromSeed(seed, dark)
    }
    if (dark && ui.amoled) {
        scheme = scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF080808),
            surfaceContainer = Color(0xFF0E0E0E),
            surfaceContainerHigh = Color(0xFF161616),
            surfaceContainerHighest = Color(0xFF1E1E1E),
        )
    }
    CompositionLocalProvider(LocalUiSettings provides ui) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
