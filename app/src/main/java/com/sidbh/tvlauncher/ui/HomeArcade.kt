package com.sidbh.tvlauncher.ui

import androidx.compose.ui.platform.LocalContext
import com.sidbh.tvlauncher.ChompSounds
import com.sidbh.tvlauncher.ChompEvent
import kotlinx.coroutines.delay
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.sidbh.tvlauncher.Dir
import com.sidbh.tvlauncher.LauncherState
import kotlin.math.abs
import kotlin.math.sin

/**
 * Chomp style: an arcade-black screen with a chomping strip, your apps as tiles, and a built-in game tile first.
 * Weather and the clock follow the arcade look; no Discover row.
 */
@Composable
fun ArcadeHome(state: LauncherState) {
    val firstFocus = remember { FocusRequester() }
    val allAppsFocus = remember { FocusRequester() }
    val gameFocus = remember { FocusRequester() }
    LaunchedEffect(state.loadedOnce, state.homeTick) {
        if (!state.loadedOnce) return@LaunchedEffect
        if (state.focusAllApps) {
            state.focusAllApps = false
            requestFocusWhenReady(allAppsFocus)
        } else {
            requestFocusWhenReady(
                when {
                    state.favorites.isNotEmpty() -> firstFocus
                    else -> gameFocus
                },
            )
        }
    }
    val appContext = LocalContext.current.applicationContext
    val clock = rememberClock()
    val mono = FontFamily.Monospace

    // The same waka-waka as in the game, as the focus moves between tiles.
    val sounds = remember { ChompSounds(appContext) }
    SideEffect { sounds.muted = !state.arcadeSound }
    DisposableEffect(Unit) { onDispose { sounds.release() } }
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(state.homeTick) {
        armed = false
        delay(700)
        armed = true
    }
    LaunchedEffect(state.focused) { if (armed) sounds.play(ChompEvent.Dot) }

    Column(Modifier.fillMaxSize().padding(top = 28.dp, bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = ContentPad), verticalAlignment = Alignment.CenterVertically) {
            ClockButton(state) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = clock.time + if (clock.meridiem.isNotEmpty()) " " + clock.meridiem else "",
                        color = ChompLime,
                        fontFamily = mono,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = weatherLine(state),
                        color = TextSecondary,
                        fontFamily = mono,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 18.dp).widthIn(max = 280.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            TopActions(state)
        }
        ChompStrip(Modifier.padding(horizontal = ContentPad, vertical = 14.dp))
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Arcade attract screen: the score to beat.
            Text("HIGH SCORE", color = Color.White, fontFamily = mono, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Text(
                text = state.chompHigh.toString().padStart(6, '0'),
                color = ChompLime,
                fontFamily = mono,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
            )
            Text("PRESS OK ON THE MANCAP TILE TO PLAY", color = TextTertiary, fontFamily = mono, fontSize = 12.sp, letterSpacing = 1.sp)
        }
        Text(
            text = (state.focused?.label ?: "").uppercase(),
            color = Color.White,
            fontFamily = mono,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = ContentPad, vertical = 6.dp),
        )
        // Down from the shelf opens All Apps.
        Column(
            Modifier.onPreviewKeyEvent { event ->
                val down = event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown
                if (down) state.showAll = true
                down
            },
        ) {
            Shelf("Favorites") {
                item(key = "game") { GameTile(state, Modifier.focusRequester(gameFocus)) { if (armed) sounds.play(ChompEvent.Dot) } }
                itemsIndexed(state.favorites, key = { _, item -> item.id }) { index, item ->
                    ShelfTile(item, state, if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                }
                item(key = "all-apps") {
                    AllAppsButton(onClick = { state.showAll = true }, modifier = Modifier.focusRequester(allAppsFocus))
                }
            }
        }
    }
}

/** The launcher's own game, as the first tile on the shelf. */
@Composable
private fun GameTile(state: LauncherState, surfaceModifier: Modifier, onFocusSound: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val labelAlpha by animateFloatAsState(if (focused) 1f else 0.7f, label = "gameLabel")
    Column(Modifier.width(TileWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        TileSurface(
            onClick = { state.showGame = true },
            modifier = surfaceModifier.fillMaxWidth(),
            onFocusChange = {
                focused = it
                if (it) onFocusSound()
            },
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.height * 0.26f
                    val cy = size.height * 0.5f
                    drawChomper(size.width * 0.28f, cy, r, Dir.Right, 32f)
                    for (i in 0..2) drawCircle(Color(0xFFFFB8AE), r * 0.2f, Offset(size.width * (0.45f + i * 0.1f), cy))
                    drawBug(size.width * 0.84f, cy, r * 0.9f, BugColors[0], Dir.Left, false, false, false, true)
                }
            }
        }
        Text(
            text = "MANCAP  HI ${state.chompHigh}",
            color = ChompLime,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(top = 8.dp).alpha(labelAlpha),
        )
    }
}

/** Decoration: Chomp eating a row of dots with the four bugs close behind. Loops forever while on screen. */
@Composable
private fun ChompStrip(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "chomp")
    val travel by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(10_000, easing = LinearEasing)),
        label = "travel",
    )
    val mouth by transition.animateFloat(
        initialValue = 4f,
        targetValue = 38f,
        animationSpec = infiniteRepeatable(tween(230, easing = LinearEasing), RepeatMode.Reverse),
        label = "mouth",
    )
    Canvas(modifier.fillMaxWidth().height(30.dp)) {
        val cy = size.height / 2f
        val r = size.height * 0.4f
        val px = (size.width + r * 12f) * travel - r * 6f
        val spacing = r * 2.4f
        var x = spacing / 2f
        while (x < size.width) {
            if (x > px + r) drawCircle(Color(0xFFFFB8AE), r * 0.2f, Offset(x, cy))
            x += spacing
        }
        drawChomper(px, cy, r, Dir.Right, mouth)
        val feet = ((travel * 10_000f / 140f).toInt() % 2) == 0
        for (i in 0..3) {
            drawBug(px - r * (3.2f + i * 2.5f), cy, r * 0.95f, BugColors[i], Dir.Right, false, false, false, feet)
        }
    }
}
