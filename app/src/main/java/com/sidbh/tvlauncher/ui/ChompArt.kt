package com.sidbh.tvlauncher.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.sidbh.tvlauncher.Dir

// Original artwork for the Mancap game: a green sprout-headed chomper and four colored beetles.
val ChompLime = Color(0xFF8CFF3F)
val MazeWall = Color(0xFF2D3BFF)
val MazeWallFill = Color(0xFF04042A)
val BugColors = listOf(Color(0xFFFF6B4A), Color(0xFFB36BFF), Color(0xFF3FC8FF), Color(0xFFFFC84A))
private val FrightBlue = Color(0xFF3A5BD9)
private val Ink = Color(0xFF14203A)
private val Leaf = Color(0xFF2E9E2A)

private fun Dir.degrees() = when (this) {
    Dir.Right -> 0f
    Dir.Down -> 90f
    Dir.Left -> 180f
    Dir.Up -> 270f
    Dir.None -> 0f
}

/**
 * The player: a round green chomper with an eye and a leafy sprout. [mouthDeg] is the half-opening of the mouth:
 * 0 is closed, 180 is gone (used for the death animation).
 */
fun DrawScope.drawChomper(cx: Float, cy: Float, r: Float, facing: Dir, mouthDeg: Float) {
    val sweep = 360f - 2f * mouthDeg
    if (sweep <= 0f) return
    drawArc(
        color = ChompLime,
        startAngle = facing.degrees() + mouthDeg,
        sweepAngle = sweep,
        useCenter = true,
        topLeft = Offset(cx - r, cy - r),
        size = Size(2f * r, 2f * r),
    )
    if (sweep > 200f) {
        // sprout on top
        drawLine(Leaf, Offset(cx, cy - r * 0.9f), Offset(cx + r * 0.18f, cy - r * 1.3f), strokeWidth = r * 0.12f, cap = StrokeCap.Round)
        drawCircle(Leaf, r * 0.2f, Offset(cx + r * 0.3f, cy - r * 1.3f))
        // eye, on the upper side of the face
        val ex: Float
        val ey: Float
        when (facing) {
            Dir.Left -> { ex = cx - r * 0.12f; ey = cy - r * 0.5f }
            Dir.Up, Dir.Down -> { ex = cx + r * 0.5f; ey = cy - r * 0.05f }
            else -> { ex = cx + r * 0.12f; ey = cy - r * 0.5f }
        }
        drawCircle(Color.White, r * 0.24f, Offset(ex, ey))
        drawCircle(Ink, r * 0.12f, Offset(ex + facing.dx * r * 0.07f, ey + facing.dy * r * 0.07f))
    }
}

/**
 * An enemy beetle: round shell, six wiggling legs, two antennae and eyes that look where it is going.
 * [eyesOnly] is a beetle hurrying home after being eaten; [frightened] beetles turn blue with X eyes.
 */
fun DrawScope.drawBug(
    cx: Float,
    cy: Float,
    r: Float,
    color: Color,
    facing: Dir,
    frightened: Boolean,
    flash: Boolean,
    eyesOnly: Boolean,
    feetPhase: Boolean,
) {
    val body = r * 0.82f
    if (!eyesOnly) {
        val c = if (frightened) (if (flash) Color.White else FrightBlue) else color
        for (side in intArrayOf(-1, 1)) {
            for (i in 0..2) {
                val y0 = cy + (i - 1) * body * 0.55f
                val wiggle = if ((i % 2 == 0) == feetPhase) 0.3f else -0.3f
                drawLine(
                    c, Offset(cx + side * body * 0.7f, y0), Offset(cx + side * body * 1.25f, y0 + wiggle * body),
                    strokeWidth = r * 0.14f, cap = StrokeCap.Round,
                )
            }
            drawLine(
                c, Offset(cx + side * body * 0.3f, cy - body * 0.75f), Offset(cx + side * body * 0.6f, cy - body * 1.2f),
                strokeWidth = r * 0.1f, cap = StrokeCap.Round,
            )
            drawCircle(c, r * 0.1f, Offset(cx + side * body * 0.6f, cy - body * 1.2f))
        }
        drawCircle(c, body, Offset(cx, cy))
        // the line down the middle of the shell
        drawLine(Color(0x33000000), Offset(cx, cy - body), Offset(cx, cy + body), strokeWidth = r * 0.08f)
    }
    if (frightened && !eyesOnly) {
        val x = if (flash) Color(0xFFFF3030) else Color.White
        for (side in intArrayOf(-1, 1)) {
            val ex = cx + side * body * 0.38f
            val ey = cy - body * 0.1f
            val d = body * 0.17f
            drawLine(x, Offset(ex - d, ey - d), Offset(ex + d, ey + d), strokeWidth = r * 0.09f, cap = StrokeCap.Round)
            drawLine(x, Offset(ex - d, ey + d), Offset(ex + d, ey - d), strokeWidth = r * 0.09f, cap = StrokeCap.Round)
        }
    } else {
        for (side in intArrayOf(-1, 1)) {
            val ex = cx + side * body * 0.38f
            val ey = cy - body * 0.1f
            drawCircle(Color.White, body * 0.3f, Offset(ex, ey))
            drawCircle(Ink, body * 0.15f, Offset(ex + facing.dx * body * 0.12f, ey + facing.dy * body * 0.12f))
        }
    }
}
