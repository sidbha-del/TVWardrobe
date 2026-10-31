package com.sidbh.tvlauncher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Original pixel art, drawn from text grids: one character per pixel, '.' is transparent.
// Atario is a small robot: teal helmet with a dark visor, orange body, gray limbs, a yellow antenna light.
private val RunnerPalette = mapOf(
    'T' to Color(0xFF2FBFB0), // helmet
    'V' to Color(0xFF10203A), // visor
    'E' to Color(0xFF9FFFF2), // eyes
    'G' to Color(0xFF9AA5B1), // limbs
    'Y' to Color(0xFFFFD21F), // antenna light
    'O' to Color(0xFFFF8A2B), // body
    'W' to Color(0xFFFFF3D6), // chest panel
)

private val RunnerRunA = listOf(
    "......Y......",
    "......G......",
    "...TTTTTTT...",
    "..TTTTTTTTT..",
    "..TVVVVVVVT..",
    "..TVEVVVEVT..",
    "..TVVVVVVVT..",
    "...TTTTTTT...",
    ".....GGG.....",
    "..OOOOOOOOO..",
    ".GOOOWWWOOOG.",
    ".GOOOWWWOOOG.",
    "..OOOOOOOOO..",
    "...GG...GG...",
    "...GG...GG...",
    "..GGG...GGG..",
)

private val RunnerRunB = RunnerRunA.take(13) + listOf(
    "....GG.GG....",
    "....GG.GG....",
    "...GGG.GGG...",
)

private val RunnerJump = RunnerRunA.take(13) + listOf(
    "..GG.....GG..",
    ".GG.......GG.",
    ".GG.......GG.",
)

private val CratePalette = mapOf(
    'D' to Color(0xFF6B3A12),
    'C' to Color(0xFFD79A4B),
    'S' to Color(0xFFFFF2B0),
)

private val CrateBlock = listOf(
    "DDDDDDDDDD",
    "DCCCCCCCCD",
    "DCCCSSCCCD",
    "DCCSSSSCCD",
    "DCSSSSSSCD",
    "DCCSSSSCCD",
    "DCCSCCSCCD",
    "DCCCCCCCCD",
    "DCCCCCCCCD",
    "DDDDDDDDDD",
)

private fun DrawScope.drawSprite(rows: List<String>, palette: Map<Char, Color>, left: Float, top: Float, px: Float) {
    for ((ry, row) in rows.withIndex()) {
        var x = 0
        while (x < row.length) {
            val c = row[x]
            var end = x
            while (end < row.length && row[end] == c) end++
            val color = palette[c]
            if (color != null) {
                drawRect(color, Offset(left + x * px, top + ry * px), Size((end - x) * px + 0.6f, px + 0.6f))
            }
            x = end
        }
    }
}

/**
 * Atario, a small robot, running across a tiled floor and jumping at each crate so a gem pops out. Loops while on
 * screen. Pure decoration; it costs a few dozen rectangles per frame.
 */
@Composable
fun RunnerStrip(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "runner")
    val travel by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing)),
        label = "travel",
    )
    Canvas(modifier.fillMaxWidth().height(72.dp)) {
        val d = density
        val ground = 14f * d
        val p = 2.6f * d
        val runnerH = 16 * p
        val runnerW = 13 * p
        val floorTop = size.height - ground
        val runnerTop = floorTop - runnerH
        val margin = 70f * d
        val x = (size.width + 2 * margin) * travel - margin // left edge of the runner
        val centerX = x + runnerW / 2f

        // Floor: slate tiles with light seams.
        drawRect(Color(0xFF334266), Offset(0f, floorTop), Size(size.width, ground))
        drawLine(Color(0xFF6C7FB0), Offset(0f, floorTop), Offset(size.width, floorTop), strokeWidth = 1.5f * d)
        drawLine(Color(0xFF1E2A47), Offset(0f, floorTop + ground / 2f), Offset(size.width, floorTop + ground / 2f), strokeWidth = 1.5f * d)
        var bx = 0f
        while (bx < size.width) {
            drawLine(Color(0xFF1E2A47), Offset(bx, floorTop), Offset(bx, floorTop + ground / 2f), strokeWidth = 1.2f * d)
            drawLine(Color(0xFF1E2A47), Offset(bx + 8f * d, floorTop + ground / 2f), Offset(bx + 8f * d, size.height), strokeWidth = 1.2f * d)
            bx += 16f * d
        }

        // Crates, a gem popping after the runner passes under each, and the jump that gets him there.
        val blockSize = 18f * d
        val blockTop = runnerTop - 16f * d
        var lift = 0f
        for (frac in floatArrayOf(0.22f, 0.55f, 0.82f)) {
            val blockCenter = size.width * frac
            drawSprite(CrateBlock, CratePalette, blockCenter - blockSize / 2f, blockTop, blockSize / 10f)
            val window = 52f * d
            val start = blockCenter - window / 2f
            val t = (centerX - start) / window
            if (t in 0f..1f) lift = maxOf(lift, sin(PI.toFloat() * t) * 24f * d)
            val u = (centerX - blockCenter) / (80f * d)
            if (u in 0f..1f) {
                val gemY = blockTop - 8f * d - sin(PI.toFloat() * u) * 34f * d
                val spin = abs(cos(u * 14f))
                val w = 7f * d * spin
                val gem = Path().apply {
                    moveTo(blockCenter, gemY - 7f * d)
                    lineTo(blockCenter + w, gemY)
                    lineTo(blockCenter, gemY + 7f * d)
                    lineTo(blockCenter - w, gemY)
                    close()
                }
                drawPath(gem, Color(0xFF5FE6FF).copy(alpha = 1f - u * 0.5f))
            }
        }

        val frame = when {
            lift > 1f -> RunnerJump
            ((travel * 16_000f / 110f).toInt() % 2) == 0 -> RunnerRunA
            else -> RunnerRunB
        }
        drawSprite(frame, RunnerPalette, x, runnerTop - lift, p)
    }
}

/** CRT scanlines over the whole screen; the lines are built once per size and drawn as a single path. */
fun Modifier.scanlines(): Modifier = drawWithCache {
    val step = 3f * density
    val path = Path()
    var y = 0f
    while (y < size.height) {
        path.moveTo(0f, y)
        path.lineTo(size.width, y)
        y += step
    }
    onDrawWithContent {
        drawContent()
        drawPath(path, Color(0x1F000000), style = Stroke(width = 1f * density))
    }
}
