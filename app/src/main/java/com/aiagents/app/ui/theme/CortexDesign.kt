package com.aiagents.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Shared visual tokens extracted from the Cortex voice assistant experience. */
object CortexColors {
    val Violet = Color(0xFF9C7CFF)
    val Blue = Color(0xFF56C7FF)
    val Pink = Color(0xFFFF72C6)
    val Mint = Color(0xFF62F5D2)

    val UserMessageBrush: Brush
        get() = Brush.linearGradient(
            listOf(Violet.copy(alpha = 0.88f), Blue.copy(alpha = 0.68f))
        )
}

@Immutable
data class CortexPalette(
    val backdropStart: Color,
    val backdropMiddle: Color,
    val backdropEnd: Color,
    val glass: Color,
    val glassStrong: Color,
    val glassSoft: Color,
    val highlight: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val violetGlow: Color,
    val blueGlow: Color,
    val pinkGlow: Color
)

val CortexDarkPalette = CortexPalette(
    backdropStart = Color(0xFF090811),
    backdropMiddle = Color(0xFF13101F),
    backdropEnd = Color(0xFF07131A),
    glass = Color(0xB31A1821),
    glassStrong = Color(0xE01A1821),
    glassSoft = Color(0x661F1C2A),
    highlight = Color(0x24FFFFFF),
    outline = Color(0x42FFFFFF),
    textPrimary = Color(0xFFF8F5FF),
    textSecondary = Color(0xBFDAD4E8),
    violetGlow = CortexColors.Violet.copy(alpha = 0.20f),
    blueGlow = CortexColors.Blue.copy(alpha = 0.15f),
    pinkGlow = CortexColors.Pink.copy(alpha = 0.10f)
)

val CortexLightPalette = CortexPalette(
    backdropStart = Color(0xFFF8F5FF),
    backdropMiddle = Color(0xFFF0F7FF),
    backdropEnd = Color(0xFFFFF5FB),
    glass = Color(0xB8FFFFFF),
    glassStrong = Color(0xE8FFFFFF),
    glassSoft = Color(0x8CFFFFFF),
    highlight = Color(0xB3FFFFFF),
    outline = Color(0x40725E9A),
    textPrimary = Color(0xFF18131F),
    textSecondary = Color(0xB35A5367),
    violetGlow = CortexColors.Violet.copy(alpha = 0.18f),
    blueGlow = CortexColors.Blue.copy(alpha = 0.14f),
    pinkGlow = CortexColors.Pink.copy(alpha = 0.12f)
)

val LocalCortexPalette = compositionLocalOf { CortexDarkPalette }

object CortexTheme {
    val colors: CortexPalette
        @Composable get() = LocalCortexPalette.current
}

@Composable
fun CortexMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val radius = size.minDimension / 2f
        drawCircle(
            brush = Brush.sweepGradient(
                listOf(
                    CortexColors.Violet,
                    CortexColors.Blue,
                    CortexColors.Mint,
                    CortexColors.Pink,
                    CortexColors.Violet
                ),
                center = center
            ),
            radius = radius
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF3A3350), Color(0xFF17131F)),
                center = center + Offset(-radius * 0.18f, -radius * 0.20f),
                radius = radius * 0.82f
            ),
            radius = radius * 0.72f
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.34f), Color.Transparent),
                center = center + Offset(-radius * 0.30f, -radius * 0.34f),
                radius = radius * 0.70f
            ),
            radius = radius * 0.72f
        )
    }
}

@Composable
fun CortexBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = CortexTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        palette.backdropStart,
                        palette.backdropMiddle,
                        palette.backdropEnd
                    ),
                    start = Offset.Zero,
                    end = Offset.Infinite
                )
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val maxDimension = maxOf(size.width, size.height)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(palette.violetGlow, Color.Transparent),
                    center = Offset(size.width * 0.12f, size.height * 0.08f),
                    radius = maxDimension * 0.58f
                ),
                radius = maxDimension * 0.58f,
                center = Offset(size.width * 0.12f, size.height * 0.08f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(palette.blueGlow, Color.Transparent),
                    center = Offset(size.width * 0.96f, size.height * 0.42f),
                    radius = maxDimension * 0.50f
                ),
                radius = maxDimension * 0.50f,
                center = Offset(size.width * 0.96f, size.height * 0.42f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(palette.pinkGlow, Color.Transparent),
                    center = Offset(size.width * 0.35f, size.height * 0.98f),
                    radius = maxDimension * 0.42f
                ),
                radius = maxDimension * 0.42f,
                center = Offset(size.width * 0.35f, size.height * 0.98f)
            )
        }
        content()
    }
}

/**
 * Gives any Compose surface the same layered translucent treatment as the assistant panel.
 * It intentionally uses translucent color rather than blur so it remains fast on API 26.
 */
fun Modifier.cortexGlass(
    shape: Shape,
    tint: Color? = null,
    borderAlpha: Float = 1f
): Modifier = composed {
    val palette = LocalCortexPalette.current
    clip(shape)
        .background(tint ?: palette.glass)
        .background(
            Brush.linearGradient(
                colors = listOf(
                    palette.highlight,
                    Color.Transparent,
                    CortexColors.Violet.copy(alpha = 0.07f)
                ),
                start = Offset.Zero,
                end = Offset.Infinite
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                listOf(
                    palette.outline.copy(alpha = borderAlpha),
                    CortexColors.Blue.copy(alpha = 0.24f * borderAlpha),
                    palette.outline.copy(alpha = 0.28f * borderAlpha)
                )
            ),
            shape = shape
        )
}
