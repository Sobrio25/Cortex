package com.aiagents.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val CortexDarkColorScheme = darkColorScheme(
    primary = CortexColors.Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xB3593F9D),
    onPrimaryContainer = Color(0xFFF7F1FF),
    secondary = CortexColors.Blue,
    onSecondary = Color(0xFF002A3C),
    secondaryContainer = Color(0x9E164B66),
    onSecondaryContainer = Color(0xFFE5F7FF),
    tertiary = CortexColors.Mint,
    onTertiary = Color(0xFF00382D),
    tertiaryContainer = Color(0x8C145B4D),
    onTertiaryContainer = Color(0xFFD9FFF5),
    error = Color(0xFFFFA8A8),
    onError = Color(0xFF5F1014),
    errorContainer = Color(0xA36D242C),
    onErrorContainer = Color(0xFFFFE1E1),
    background = Color.Transparent,
    onBackground = CortexDarkPalette.textPrimary,
    surface = CortexDarkPalette.glass,
    onSurface = CortexDarkPalette.textPrimary,
    surfaceVariant = CortexDarkPalette.glassSoft,
    onSurfaceVariant = CortexDarkPalette.textSecondary,
    outline = CortexDarkPalette.outline,
    outlineVariant = Color(0x2EFFFFFF),
    inverseSurface = Color(0xFFEDE7F6),
    inverseOnSurface = Color(0xFF241F2B),
    inversePrimary = Color(0xFF6447BD),
    surfaceTint = CortexColors.Violet,
    scrim = Color(0xA6000000)
)

private val CortexLightColorScheme = lightColorScheme(
    primary = Color(0xFF6748C8),
    onPrimary = Color.White,
    primaryContainer = Color(0x99E6DCFF),
    onPrimaryContainer = Color(0xFF271153),
    secondary = Color(0xFF19759C),
    onSecondary = Color.White,
    secondaryContainer = Color(0x99D6F1FF),
    onSecondaryContainer = Color(0xFF07364A),
    tertiary = Color(0xFF137A68),
    onTertiary = Color.White,
    tertiaryContainer = Color(0x99C8F8EC),
    onTertiaryContainer = Color(0xFF073C32),
    error = Color(0xFFB3262D),
    onError = Color.White,
    errorContainer = Color(0xA3FFDAD9),
    onErrorContainer = Color(0xFF4B0A10),
    background = Color.Transparent,
    onBackground = CortexLightPalette.textPrimary,
    surface = CortexLightPalette.glass,
    onSurface = CortexLightPalette.textPrimary,
    surfaceVariant = CortexLightPalette.glassSoft,
    onSurfaceVariant = CortexLightPalette.textSecondary,
    outline = CortexLightPalette.outline,
    outlineVariant = Color(0x33725E9A),
    inverseSurface = Color(0xFF2F2938),
    inverseOnSurface = Color(0xFFF8F5FF),
    inversePrimary = Color(0xFFCDBDFF),
    surfaceTint = CortexColors.Violet,
    scrim = Color(0x660B0711)
)

@Composable
fun AIAgentsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    expressive: Boolean = true,
    transparentSystemBars: Boolean = true,
    applyBackdrop: Boolean = true,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) CortexDarkPalette else CortexLightPalette
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> CortexDarkColorScheme
        else -> CortexLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = if (transparentSystemBars) {
                android.graphics.Color.TRANSPARENT
            } else {
                palette.glassStrong.toArgb()
            }
            window.navigationBarColor = if (transparentSystemBars) {
                android.graphics.Color.TRANSPARENT
            } else {
                palette.glassStrong.toArgb()
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalCortexPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = if (expressive) ExpressiveTypography else AppTypography,
            shapes = if (expressive) ExpressiveShapes else AppShapes
        ) {
            if (applyBackdrop) {
                CortexBackdrop { content() }
            } else {
                content()
            }
        }
    }
}
