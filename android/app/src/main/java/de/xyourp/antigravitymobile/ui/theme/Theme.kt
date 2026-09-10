package de.xyourp.antigravitymobile.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = SeaGlassMint,
    onPrimary = Color(0xFF1D2021),
    primaryContainer = SeaGlassContainer,
    onPrimaryContainer = SeaGlassAquamarine,
    secondary = SeaGlassLightGreen,
    onSecondary = Color(0xFF1D2021),
    secondaryContainer = SeaGlassSecondaryContainer,
    onSecondaryContainer = SeaGlassMint,
    tertiary = SeaGlassMoss,
    onTertiary = Color(0xFF1D2021),
    tertiaryContainer = Color(0xFF283626),
    onTertiaryContainer = SeaGlassTan,
    background = SeaGlassDarkPrimary,
    onBackground = SeaGlassTan,
    surface = SeaGlassDarkPrimary,
    onSurface = SeaGlassTan,
    surfaceVariant = SeaGlassDarkSecondary,
    onSurfaceVariant = SeaGlassMuted,
    surfaceTint = SeaGlassMint,
    surfaceContainerLowest = SeaGlassDarkSecondary,
    surfaceContainerLow = SeaGlassDarkSecondary,
    surfaceContainer = SeaGlassDarkSecondary,
    surfaceContainerHigh = SeaGlassDarkSurfaceVariant,
    surfaceContainerHighest = Color(0xFF35393B),
    outline = SeaGlassBorder,
    outlineVariant = SeaGlassBorder,
    inverseSurface = SeaGlassTan,
    inverseOnSurface = SeaGlassDarkPrimary,
    inversePrimary = SeaGlassMoss,
    scrim = Color(0xFF000000),
    error = RejectRed,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5A1E28),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColors = lightColorScheme(
    primary = SeaGlassMoss,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2EED8),
    onPrimaryContainer = Color(0xFF1B3012),
    secondary = SeaGlassMint,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0F3EA),
    onSecondaryContainer = Color(0xFF103324),
    tertiary = SeaGlassMoss,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEAF5E5),
    onTertiaryContainer = Color(0xFF1B3012),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = SeaGlassMoss,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = Color(0xFFF2ECE0),
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = Color(0xFFE6DFC9),
    surfaceContainerHighest = Color(0xFFDDD5BD),
    outline = LightOutline,
    outlineVariant = LightOutline,
    inverseSurface = LightOnSurface,
    inverseOnSurface = LightBackground,
    inversePrimary = SeaGlassMint,
    scrim = Color(0xFF000000),
    error = RejectRed,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

@Composable
fun AntigravityTheme(
    desktopTokens: Map<String, String> = emptyMap(),
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    fun token(name: String, fallback: Color): Color = runCatching { Color(android.graphics.Color.parseColor(desktopTokens[name])) }.getOrDefault(fallback)

    val primaryAccent = token("--interactive-accent", baseScheme.primary)
    val bgPrimary = token("--background-primary", baseScheme.background)
    val bgSecondary = token("--background-secondary", baseScheme.surfaceVariant)
    val textNormal = token("--text-normal", baseScheme.onBackground)
    val textMuted = token("--text-muted", baseScheme.onSurfaceVariant)
    val borderMod = token("--background-modifier-border", baseScheme.outline)
    val colorRed = token("--color-red", baseScheme.error)

    val colorScheme = if (desktopTokens.isEmpty()) baseScheme else baseScheme.copy(
        primary = primaryAccent,
        primaryContainer = primaryAccent.copy(alpha = 0.18f),
        onPrimaryContainer = textNormal,
        secondary = token("--color-light-green", baseScheme.secondary),
        secondaryContainer = primaryAccent.copy(alpha = 0.12f),
        onSecondaryContainer = primaryAccent,
        background = bgPrimary,
        surface = bgPrimary,
        surfaceVariant = bgSecondary,
        surfaceContainer = bgSecondary,
        surfaceContainerHigh = token("--background-modifier-border-hover", bgSecondary),
        onBackground = textNormal,
        onSurface = textNormal,
        onSurfaceVariant = textMuted,
        outline = borderMod,
        outlineVariant = borderMod,
        error = colorRed,
    )
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = colorScheme.background.luminance() > 0.5f
            controller.isAppearanceLightNavigationBars = colorScheme.background.luminance() > 0.5f
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
