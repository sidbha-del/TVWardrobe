package com.sidbh.tvlauncher.ui

import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.sidbh.tvlauncher.HomeStyle
import androidx.tv.material3.ButtonColors
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// About 6% of a 960 dp wide screen: inside the safe area of TVs that overscan.
val ContentPad = 56.dp

val BgMid = Color(0xFF111520)
val BgBottom = Color(0xFF0B0D12)
val DefaultTint = Color(0xFF2A3A5A)
val InputTint = Color(0xFF3A4A6B)

val TileCard = Brush.verticalGradient(listOf(Color(0xFF2C3340), Color(0xFF1B2029)))
val TextPrimary = Color(0xFFF5F6F8)
val TextSecondary = Color(0xB3F5F6F8)
val TextTertiary = Color(0x80F5F6F8)
val FocusGlow = Color(0x4DFFFFFF)

/** Top color of the background gradient: the artwork's color, kept dark and muted so white text stays readable. */
fun tintTop(tint: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(tint.toArgb(), hsv)
    hsv[1] = hsv[1].coerceAtMost(0.7f)
    hsv[2] = hsv[2].coerceIn(0.14f, 0.38f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}

/** Colors that tell the home styles apart at a glance: background, focus highlight and glow. */
class StyleColors(
    val bgMid: Color,
    val bgBottom: Color,
    val accent: Color,
    val glow: Color,
    val panel: Color, // sheets, menus and dialogs
    val weatherTop: Color, // top of the weather page gradient
    val flat: Boolean = false,
    val glass: Boolean = false, // glass: translucent surfaces with bright rims
)

private val ClassicColors = StyleColors(BgMid, BgBottom, Color.White, FocusGlow, Color(0xFF1B2029), Color(0xFF1B2A44)) // navy, white focus
private val GlassColors = StyleColors(Color(0xFF1B1733), Color(0xFF0A0916), Color.White, Color(0x66FFFFFF), Color(0xFF2B2550), Color(0xFF3B2F6B), glass = true) // glass over violet, white focus
private val ListColors = StyleColors(Color.Black, Color.Black, Color(0xFFFFC857), Color(0x00000000), Color(0xFF191610), Color(0xFF1F1A0E), flat = true) // black, amber

private val RetroColors = StyleColors(Color(0xFF090A2E), Color(0xFF05061C), Color(0xFFFF4F3D), Color(0x66FF4F3D), Color(0xFF14164A), Color(0xFF1A1D6B), flat = true) // console blue and Atario red

private val ArcadeColors = StyleColors(Color(0xFF05061A), Color(0xFF000008), Color(0xFFFFE31A), Color(0x66FFE31A), Color(0xFF0A0A2A), Color(0xFF101060), flat = true) // arcade black, blue and yellow

val LocalStyleColors = compositionLocalOf { ClassicColors }

fun HomeStyle.colors(): StyleColors = when (this) {
    HomeStyle.Classic -> ClassicColors
    HomeStyle.Glass -> GlassColors
    HomeStyle.ListStyle -> ListColors
    HomeStyle.Arcade -> ArcadeColors
    HomeStyle.Retro -> RetroColors
}

/** Buttons in sheets and menus: translucent at rest, the style's accent when focused. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun styledButtonColors(): ButtonColors {
    val accent = LocalStyleColors.current.accent
    val glass = LocalStyleColors.current.glass
    return ButtonDefaults.colors(
        containerColor = if (glass) Color(0x2EFFFFFF) else Color(0x22FFFFFF),
        contentColor = TextPrimary,
        focusedContainerColor = if (glass) Color(0xEBFFFFFF) else accent,
        focusedContentColor = Color.Black,
        pressedContainerColor = accent.copy(alpha = 0.85f),
        pressedContentColor = Color.Black,
    )
}

// ---- glass ----
// Android 11 has no live background blur, so glass is built from what is available: a vivid, artwork-tinted
// backdrop, a translucent gradient fill, a specular rim that is bright at the top-left and bottom-right, a sheen
// across the face, and a soft shadow underneath.

/** The bright specular edge of a glass shape. */
val GlassRim = Brush.linearGradient(listOf(Color(0xCCFFFFFF), Color(0x14FFFFFF), Color(0x59FFFFFF)))

/** A diagonal highlight laid over the face of glass, like light catching a curved surface. */
val GlassSheen = Brush.linearGradient(listOf(Color(0x24FFFFFF), Color(0x00FFFFFF), Color(0x0AFFFFFF)))

private val GlassFill = Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x14FFFFFF)))

fun Modifier.glass(shape: Shape, tint: Color = Color.Transparent): Modifier =
    shadow(10.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x66000000))
        .background(tint, shape)
        .background(GlassFill, shape)
        .border(1.dp, GlassRim, shape)

/** Background of sheets, menus and dialogs: glass in the Glass style, a solid panel in the others. */
@Composable
fun Modifier.panelSurface(shape: Shape, alpha: Float = 1f, glassShape: Shape = shape): Modifier {
    val c = LocalStyleColors.current
    return if (c.glass) glass(glassShape, c.panel.copy(alpha = 0.62f)) else background(c.panel.copy(alpha = alpha), shape)
}
