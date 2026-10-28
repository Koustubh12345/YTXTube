package com.tensei.ytxtube

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow









@Composable
fun glassFill(alpha: Float = 1f): Color {
    val dark = isSystemInDarkTheme()
    return if (dark) Color(0xFF121212).copy(alpha = 0.38f * alpha)
    else Color(0xFFFAFAFA).copy(alpha = 0.34f * alpha)
}

/** The backdrop captured behind the player, used by in-player liquid glass controls. */
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Immutable
data class GlassSpec(
    val shape: Shape,
    val tint: Color,
    val blur: Dp,
    val refractionHeight: Dp,
    val refractionAmount: Dp,
    val shadow: Shadow?,
    val highlight: Highlight?,
    val innerShadow: InnerShadow?,
    val chromaticAberration: Boolean,
)

@Composable
fun glassSpec(
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
    tint: Color = glassFill(),
    blur: Dp = 18.dp,
    refractionHeight: Dp = 14.dp,
    refractionAmount: Dp = 22.dp,
    shadow: Shadow? = Shadow.Default,
    highlight: Highlight? = Highlight.Default,
    innerShadow: InnerShadow? = InnerShadow.Default.copy(radius = 10.dp, alpha = 0.5f),
    chromaticAberration: Boolean = true,
): GlassSpec = GlassSpec(
    shape = shape,
    tint = tint,
    blur = blur,
    refractionHeight = refractionHeight,
    refractionAmount = refractionAmount,
    shadow = shadow,
    highlight = highlight,
    innerShadow = innerShadow,
    chromaticAberration = chromaticAberration,
)


fun Modifier.liquidGlass(backdrop: Backdrop, spec: GlassSpec): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { spec.shape },
    effects = {
        vibrancy()
        blur(spec.blur.toPx())
        lens(
            spec.refractionHeight.toPx(),
            spec.refractionAmount.toPx(),
            chromaticAberration = spec.chromaticAberration,
        )
    },
    highlight = spec.highlight?.let { h -> { h } },
    shadow = spec.shadow?.let { s -> { s } },
    innerShadow = spec.innerShadow?.let { s -> { s } },
    onDrawSurface = {
        if (spec.tint.isSpecified) drawRect(spec.tint)
    },
)
