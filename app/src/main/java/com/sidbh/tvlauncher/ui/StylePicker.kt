package com.sidbh.tvlauncher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.sidbh.tvlauncher.Dir
import com.sidbh.tvlauncher.HomeStyle
import com.sidbh.tvlauncher.LauncherState

private fun HomeStyle.blurb(count: Int) = when (this) {
    HomeStyle.Classic -> "Hero, Discover row and a favorites dock"
    HomeStyle.Glass -> "Glass with a big top-shelf card"
    HomeStyle.ListStyle -> "Black list with icons, line selection"
    HomeStyle.Arcade -> "Arcade look with a playable game"
    HomeStyle.Retro -> "$count-in-1 console menu with sounds"
}

/** Pick a home style from a row of cards, each with a drawn preview of its layout and colors. */
@Composable
fun StylePicker(state: LauncherState) {
    val current = remember { FocusRequester() }
    Dialog(
        onDismissRequest = { state.showStylePicker = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .fillMaxWidth(0.95f)
                    .panelSurface(RoundedCornerShape(24.dp), glassShape = RoundedCornerShape(30.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Home style", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Pick a look. Only the style you choose is loaded, so the others cost nothing.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeStyle.entries.forEach { style ->
                        StyleCard(
                            style = style,
                            blurb = style.blurb(state.items.size),
                            selected = style == state.homeStyle,
                            modifier = Modifier.weight(1f).let { if (style == state.homeStyle) it.focusRequester(current) else it },
                            onPick = {
                                state.chooseHomeStyle(style)
                                state.showStylePicker = false
                            },
                        )
                    }
                }
            }
        }
        LaunchedEffect(Unit) { requestFocusWhenReady(current) }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StyleCard(style: HomeStyle, blurb: String, selected: Boolean, modifier: Modifier, onPick: () -> Unit) {
    val own = remember { FocusRequester() }
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onPick,
        modifier = modifier.focusRequester(own).hoverFocus(own).mouseClick(onPick),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Color(0xFFFFFFFF) else Color(0x33FFFFFF)), shape = shape),
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape),
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x1AFFFFFF),
            focusedContainerColor = Color(0x33FFFFFF),
            pressedContainerColor = Color(0x44FFFFFF),
        ),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))) { drawStylePreview(style) }
            Text(
                text = if (selected) "${style.label}  ✓" else style.label,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = blurb, color = TextTertiary, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2)
        }
    }
}

// ---- previews: a tiny schematic of each layout, in that style's own colors ----

private fun DrawScope.box(c: Color, x: Float, y: Float, w: Float, h: Float, r: Float = 0f) =
    drawRoundRect(c, Offset(size.width * x, size.height * y), Size(size.width * w, size.height * h), CornerRadius(size.height * r))

private fun DrawScope.drawStylePreview(style: HomeStyle) {
    val colors = style.colors()
    when (style) {
        HomeStyle.Classic -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF2A3A5A), colors.bgMid, colors.bgBottom)))
            box(Color.White.copy(alpha = 0.9f), 0.06f, 0.08f, 0.16f, 0.09f, 0.02f)
            for (i in 0..3) box(Color.White.copy(alpha = 0.3f), 0.5f + i * 0.11f, 0.09f, 0.09f, 0.06f, 0.03f)
            box(Color.White.copy(alpha = 0.7f), 0.06f, 0.27f, 0.3f, 0.05f)
            box(Color.White.copy(alpha = 0.35f), 0.06f, 0.36f, 0.22f, 0.04f)
            box(Color.White.copy(alpha = 0.85f), 0.66f, 0.22f, 0.28f, 0.22f, 0.04f)
            for (i in 0..3) box(Color(0xFF6A7FA8), 0.06f + i * 0.23f, 0.52f, 0.2f, 0.17f, 0.03f)
            for (i in 0..5) box(Color(0xFFCBD3E0), 0.06f + i * 0.155f, 0.78f, 0.13f, 0.14f, 0.03f)
        }
        HomeStyle.Glass -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF5A4A9C), colors.bgMid, colors.bgBottom)))
            drawCircle(Color(0x66FF7AB6), size.width * 0.35f, Offset(size.width * 0.9f, size.height * 1.0f))
            box(Color.White.copy(alpha = 0.28f), 0.22f, 0.06f, 0.46f, 0.1f, 0.05f)
            box(Color.White.copy(alpha = 0.28f), 0.78f, 0.06f, 0.16f, 0.1f, 0.05f)
            box(Color.White.copy(alpha = 0.22f), 0.06f, 0.22f, 0.88f, 0.36f, 0.09f)
            box(Color.White.copy(alpha = 0.55f), 0.1f, 0.46f, 0.25f, 0.05f)
            for (i in 0..4) box(Color.White.copy(alpha = 0.26f), 0.06f + i * 0.18f, 0.68f, 0.16f, 0.22f, 0.06f)
        }
        HomeStyle.ListStyle -> {
            drawRect(Color.Black)
            box(Color.White.copy(alpha = 0.85f), 0.06f, 0.07f, 0.18f, 0.1f)
            val icons = listOf(Color(0xFFE53935), Color(0xFF3F8CFF), Color(0xFFFFFFFF), Color(0xFF4CAF50), Color(0xFFFFB300))
            for (i in 0..4) {
                val y = 0.26f + i * 0.14f
                box(icons[i], 0.08f, y, 0.07f, 0.1f, 0.02f)
                box(if (i == 2) Color(0xFFFFC857) else Color.White.copy(alpha = 0.8f), 0.19f, y + 0.03f, 0.3f + (i % 3) * 0.08f, 0.04f)
            }
            // The selected row is marked by a line above and below, in the accent color.
            box(Color(0xFFFFC857), 0.06f, 0.5f, 0.88f, 0.012f)
            box(Color(0xFFFFC857), 0.06f, 0.625f, 0.88f, 0.012f)
        }
        HomeStyle.Arcade -> {
            drawRect(colors.bgMid)
            for (i in 0..2) box(MazeWall, 0.06f + i * 0.31f, 0.1f, 0.25f, 0.015f)
            box(ChompLime, 0.06f, 0.1f, 0.2f, 0.07f)
            drawChomper(size.width * 0.12f, size.height * 0.42f, size.height * 0.15f, Dir.Right, 32f)
            for (i in 0..6) drawCircle(Color(0xFFFFB8AE), size.height * 0.025f, Offset(size.width * (0.26f + i * 0.07f), size.height * 0.42f))
            drawBug(size.width * 0.84f, size.height * 0.42f, size.height * 0.14f, BugColors[0], Dir.Left, false, false, false, true)
            for (i in 0..4) box(Color(0xFF3A3A55), 0.06f + i * 0.18f, 0.66f, 0.16f, 0.24f, 0.04f)
            box(ChompLime, 0.06f, 0.66f, 0.16f, 0.24f, 0.04f)
        }
        HomeStyle.Retro -> {
            drawRect(colors.bgMid)
            val title = listOf(0xFFFF4F3D, 0xFFFF9A1F, 0xFFFFE23F, 0xFF54D66A, 0xFF3FC8FF, 0xFFB66CFF)
            for (i in title.indices) box(Color(title[i]), 0.06f + i * 0.055f, 0.07f, 0.045f, 0.12f)
            // The runner robot on a tiled floor, next to a crate.
            box(Color(0xFF334266), 0f, 0.3f, 1f, 0.05f)
            box(Color(0xFF2FBFB0), 0.7f, 0.2f, 0.04f, 0.04f)
            box(Color(0xFFFF8A2B), 0.7f, 0.24f, 0.04f, 0.04f)
            box(Color(0xFFD79A4B), 0.86f, 0.14f, 0.05f, 0.08f)
            drawRect(Color.White, Offset(size.width * 0.05f, size.height * 0.4f), Size(size.width * 0.9f, size.height * 0.52f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.height * 0.02f))
            for (i in 0..3) {
                val y = 0.46f + i * 0.11f
                box(Color(0xFF8C8CFF), 0.12f, y, 0.06f, 0.04f)
                box(if (i == 1) Color.White else Color(0xFFD8D8F8), 0.22f, y, 0.3f + (i % 2) * 0.12f, 0.04f)
            }
            box(Color(0xFFFF4F3D), 0.08f, 0.57f, 0.03f, 0.05f)
        }
    }
}
