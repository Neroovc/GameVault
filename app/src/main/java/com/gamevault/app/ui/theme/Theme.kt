package com.gamevault.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.gamevault.app.data.settings.ColorPalette
import com.gamevault.app.data.settings.ThemeMode

private data class PaletteHues(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
)

private val paletteHues: Map<ColorPalette, PaletteHues> = mapOf(
    ColorPalette.VIOLET to PaletteHues(Color(0xFF7C4DFF), Color(0xFF03DAC6), Color(0xFFE040FB)),
    ColorPalette.SUNSET to PaletteHues(Color(0xFFFF7043), Color(0xFF5C6BC0), Color(0xFFFFB300)),
    ColorPalette.OCEAN to PaletteHues(Color(0xFF00A6B4), Color(0xFF7C4DFF), Color(0xFF00BFA5)),
    ColorPalette.FOREST to PaletteHues(Color(0xFF2E7D32), Color(0xFF7CB342), Color(0xFF00A6B4)),
    ColorPalette.GOLD to PaletteHues(Color(0xFFD4A900), Color(0xFF8D6E63), Color(0xFFFFB300)),
)

/** Light [ColorScheme] for a [ColorPalette], with the shared light surface tokens. */
fun lightSchemeFor(palette: ColorPalette): ColorScheme {
    val hues = paletteHues.getValue(palette)
    return lightColorScheme(
        primary = hues.primary,
        secondary = hues.secondary,
        tertiary = hues.tertiary,
        surface = Color(0xFFFFFBFE),
        background = Color(0xFFFFFBFE),
        onPrimary = Color.White,
        onSecondary = Color.Black,
        onBackground = Color(0xFF1C1B1F),
        onSurface = Color(0xFF1C1B1F),
    )
}

/** Dark [ColorScheme] for a [ColorPalette], with the shared dark surface tokens. */
fun darkSchemeFor(palette: ColorPalette): ColorScheme {
    val hues = paletteHues.getValue(palette)
    return darkColorScheme(
        primary = hues.primary,
        secondary = hues.secondary,
        tertiary = hues.tertiary,
        surface = Color(0xFF1C1B1F),
        background = Color(0xFF121212),
        onPrimary = Color.White,
        onSecondary = Color.Black,
        onBackground = Color(0xFFE6E1E5),
        onSurface = Color(0xFFE6E1E5),
    )
}

/**
 * Copy [base] and force every surface/container slot to (near-)black so AMOLED
 * displays render true black. Accent hues (primary/secondary/tertiary) are kept.
 */
fun pureBlackScheme(base: ColorScheme): ColorScheme = base.copy(
    surface = Color.Black,
    background = Color.Black,
    surfaceVariant = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerHigh = Color.Black,
    surfaceContainerHighest = Color.Black,
    primaryContainer = Color(0xFF0A0A0A),
    secondaryContainer = Color(0xFF0A0A0A),
    tertiaryContainer = Color(0xFF0A0A0A),
)

/**
 * Resolve the effective dark-theme flag from a [ThemeMode].
 */
@Composable
private fun resolveDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun GameVaultTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    palette: ColorPalette = ColorPalette.VIOLET,
    amoledDark: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = resolveDarkTheme(themeMode)

    val colorScheme = when {
        amoledDark && darkTheme -> pureBlackScheme(darkSchemeFor(palette))
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> darkSchemeFor(palette)
        else -> lightSchemeFor(palette)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
