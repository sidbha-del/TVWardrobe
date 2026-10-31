package com.sidbh.tvlauncher.ui

import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.sidbh.tvlauncher.Dir
import com.sidbh.tvlauncher.BugMode
import com.sidbh.tvlauncher.LauncherState
import com.sidbh.tvlauncher.ChompEngine
import com.sidbh.tvlauncher.ChompMaze
import com.sidbh.tvlauncher.ChompSounds
import com.sidbh.tvlauncher.ChompPhase
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private data class Hud(val score: Int, val high: Int, val lives: Int, val level: Int, val phase: ChompPhase, val paused: Boolean)

/**
 * Full-screen Chomp. Remote: D-pad steers, OK pauses (or restarts after game over). Mouse: move or click to
 * steer toward the cursor, right click pauses. Back leaves the game.
 */
@Composable
fun ChompGame(state: LauncherState) {
    val appContext = LocalContext.current.applicationContext
    val engine = remember { ChompEngine(highScore = state.chompHigh) }
    val sounds = remember { ChompSounds(appContext).also { it.muted = !state.arcadeSound } }
    var muted by remember { mutableStateOf(!state.arcadeSound) }
    var frame by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var hud by remember { mutableStateOf(Hud(0, state.chompHigh, 2, 1, ChompPhase.Ready, false)) }
    val focus = remember { FocusRequester() }
    val view = LocalView.current

    // Keep the TV awake while playing, and save the high score however the game ends.
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            sounds.release()
            state.recordChompScore(engine.score)
        }
    }
    LaunchedEffect(Unit) { requestFocusWhenReady(focus) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else min(0.05f, (now - last) / 1_000_000_000f)
                last = now
                if (!paused) engine.update(dt)
                while (engine.events.isNotEmpty()) sounds.play(engine.events.removeFirst())
                frame++
                val h = Hud(engine.score, engine.highScore, engine.lives, engine.level, engine.phase, paused)
                if (h != hud) {
                    if (h.phase == ChompPhase.GameOver && hud.phase != ChompPhase.GameOver) state.recordChompScore(engine.score)
                    hud = h
                }
            }
        }
    }

    val toggleMute = {
        state.toggleArcadeSound()
        muted = !state.arcadeSound
        sounds.muted = muted
        if (muted) sounds.stopAll()
    }
    val action = {
        if (engine.phase == ChompPhase.GameOver) {
            engine.restart()
            paused = false
        } else {
            paused = !paused
            if (paused) sounds.stopAll()
        }
    }

    Dialog(
        onDismissRequest = { state.showGame = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionUp -> engine.steer(Dir.Up)
                        Key.DirectionDown -> engine.steer(Dir.Down)
                        Key.DirectionLeft -> engine.steer(Dir.Left)
                        Key.DirectionRight -> engine.steer(Dir.Right)
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> action()
                        Key.Menu, Key.M -> toggleMute()
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LeftHud(hud, Modifier.width(210.dp))
            Box(Modifier.fillMaxHeight().aspectRatio(ChompMaze.W / ChompMaze.H.toFloat())) {
                Maze(engine, { frame }, onAction = action, onSecondary = { paused = !paused })
                val label = when {
                    hud.paused -> "PAUSED"
                    hud.phase == ChompPhase.Ready -> "READY!"
                    hud.phase == ChompPhase.GameOver -> "GAME  OVER"
                    else -> null
                }
                if (label != null) {
                    Text(
                        text = label,
                        color = if (hud.phase == ChompPhase.GameOver) Color(0xFFFF3030) else ChompLime,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        modifier = Modifier.align(BiasAlignment(0f, 0.12f)),
                    )
                }
            }
            RightHud(hud, muted, toggleMute, Modifier.width(210.dp))
        }
    }
}

@Composable
private fun Maze(engine: ChompEngine, frame: () -> Int, onAction: () -> Unit, onSecondary: () -> Unit) {
    Canvas(
        Modifier
            .fillMaxSize()
            // Mouse (or touch): the cursor steers Chomp toward it; right click pauses; click restarts after game over.
            .pointerInput(engine) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            onSecondary()
                            continue
                        }
                        if (event.type == PointerEventType.Press && engine.phase == ChompPhase.GameOver) {
                            onAction()
                            continue
                        }
                        if (event.type != PointerEventType.Move && event.type != PointerEventType.Press) continue
                        val t = size.width / ChompMaze.W.toFloat()
                        val vx = change.position.x - (engine.player.x + 0.5f) * t
                        val vy = change.position.y - (engine.player.y + 0.5f) * t
                        if (hypot(vx, vy) < 0.4f * t) continue
                        engine.steer(
                            if (abs(vx) > abs(vy)) (if (vx > 0) Dir.Right else Dir.Left) else (if (vy > 0) Dir.Down else Dir.Up),
                        )
                    }
                }
            }
            .drawWithCache {
                val t = size.width / ChompMaze.W
                // Walls and their glowing edges are built once per size; only Chomp, bugs and dots move.
                val fill = Path()
                val edge = Path()
                for (y in 0 until ChompMaze.H) for (x in 0 until ChompMaze.W) {
                    if (!ChompMaze.isWall(x, y)) continue
                    fill.addRect(Rect(x * t, y * t, (x + 1) * t, (y + 1) * t))
                    fun open(nx: Int, ny: Int) = nx in 0 until ChompMaze.W && ny in 0 until ChompMaze.H && !ChompMaze.isWall(nx, ny)
                    if (open(x, y - 1)) { edge.moveTo(x * t, y * t); edge.lineTo((x + 1) * t, y * t) }
                    if (open(x, y + 1)) { edge.moveTo(x * t, (y + 1) * t); edge.lineTo((x + 1) * t, (y + 1) * t) }
                    if (open(x - 1, y)) { edge.moveTo(x * t, y * t); edge.lineTo(x * t, (y + 1) * t) }
                    if (open(x + 1, y)) { edge.moveTo((x + 1) * t, y * t); edge.lineTo((x + 1) * t, (y + 1) * t) }
                }
                onDrawBehind {
                    frame() // reading the frame counter here is what makes the canvas redraw every frame
                    val flashing = engine.phase == ChompPhase.LevelClear && (engine.anim * 6f).toInt() % 2 == 0
                    drawPath(fill, MazeWallFill)
                    drawPath(edge, if (flashing) Color.White else MazeWall, style = Stroke(width = t * 0.13f, cap = StrokeCap.Round))
                    // The bug house door.
                    drawLine(
                        Color(0xFFFFB8FF),
                        Offset(ChompMaze.DOOR_X * t, (ChompMaze.DOOR_Y + 0.5f) * t),
                        Offset((ChompMaze.DOOR_X + 1) * t, (ChompMaze.DOOR_Y + 0.5f) * t),
                        strokeWidth = t * 0.14f,
                    )
                    val blink = (engine.anim * 3f).toInt() % 2 == 0
                    for (y in 0 until ChompMaze.H) for (x in 0 until ChompMaze.W) {
                        when (engine.dots[y][x]) {
                            '.' -> drawCircle(Color(0xFFFFB8AE), t * 0.09f, Offset((x + 0.5f) * t, (y + 0.5f) * t))
                            'o' -> if (blink) drawCircle(Color(0xFFFFB8AE), t * 0.26f, Offset((x + 0.5f) * t, (y + 0.5f) * t))
                        }
                    }
                    val player = engine.player
                    val r = t * 0.46f
                    val moving = engine.phase == ChompPhase.Playing && player.dir != Dir.None
                    when (engine.phase) {
                        ChompPhase.Dying -> {
                            val progress = (1f - engine.timer / 1.6f).coerceIn(0f, 1f)
                            drawChomper((player.x + 0.5f) * t, (player.y + 0.5f) * t, r, Dir.Up, 20f + 160f * progress)
                        }
                        else -> {
                            val mouth = if (moving) 8f + 34f * abs(sin(engine.anim * 16f)) else 24f
                            drawChomper((player.x + 0.5f) * t, (player.y + 0.5f) * t, r, engine.facing, mouth)
                        }
                    }
                    if (engine.phase != ChompPhase.Dying) {
                        val fright = engine.frightTimer
                        val flash = fright in 0.01f..2f && (engine.anim * 6f).toInt() % 2 == 0
                        val feet = (engine.anim * 8f).toInt() % 2 == 0
                        for (g in engine.bugs) {
                            // Bugs waiting in the house bob in place.
                            val bob = if (g.mode == BugMode.House && g.releaseIn > 0f) sin(engine.anim * 8f + g.id) * 0.12f else 0f
                            drawBug(
                                (g.x + 0.5f) * t, (g.y + 0.5f + bob) * t, r, BugColors[g.id], g.dir,
                                frightened = g.frightened && g.mode != BugMode.Eaten, flash = flash,
                                eyesOnly = g.mode == BugMode.Eaten, feetPhase = feet,
                            )
                        }
                    }
                }
            },
    ) {}
}

@Composable
private fun LeftHud(hud: Hud, modifier: Modifier) {
    Column(modifier.padding(start = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HudLabel("1UP")
        HudValue(hud.score.toString().padStart(6, '0'))
        Spacer(Modifier.size(12.dp))
        HudLabel("HIGH SCORE")
        HudValue(hud.high.toString().padStart(6, '0'))
        Spacer(Modifier.size(12.dp))
        HudLabel("LEVEL")
        HudValue(hud.level.toString())
    }
}

@Composable
private fun RightHud(hud: Hud, muted: Boolean, onToggleMute: () -> Unit, modifier: Modifier) {
    Column(modifier.padding(end = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
        HudLabel("LIVES")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(hud.lives.coerceIn(0, 6)) {
                Canvas(Modifier.size(22.dp)) { drawChomper(size.width / 2, size.height / 2, size.width * 0.45f, Dir.Right, 30f) }
            }
        }
        Spacer(Modifier.size(18.dp))
        Text("REMOTE", color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Text("D-pad steers", color = TextTertiary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Text("OK pauses", color = TextTertiary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Spacer(Modifier.size(6.dp))
        Text("MOUSE", color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Text("move or click to steer", color = TextTertiary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Text("right click pauses", color = TextTertiary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Spacer(Modifier.size(6.dp))
        Text("BACK exits", color = TextTertiary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Spacer(Modifier.size(10.dp))
        // Tap-only (no focus), so it never steals the D-pad from the game. MENU or M does the same.
        Text(
            text = if (muted) "SOUND OFF" else "SOUND ON",
            color = if (muted) TextTertiary else ChompLime,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.pointerInput(Unit) { detectTapGestures { onToggleMute() } },
        )
        Text("MENU / click toggles", color = TextTertiary, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    }
}

@Composable
private fun HudLabel(text: String) =
    Text(text, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)

@Composable
private fun HudValue(text: String) =
    Text(text, color = ChompLime, fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold)
