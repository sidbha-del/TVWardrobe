package com.sidbh.tvlauncher.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.sidbh.tvlauncher.Item
import com.sidbh.tvlauncher.LauncherState
import com.sidbh.tvlauncher.RetroSounds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PAGE = 10
private val RowHeight = 30.dp
private val Mono = FontFamily.Monospace
private val HardShadow = TextStyle(shadow = Shadow(Color.Black, Offset(3f, 3f), 0f))
private val RetroBlue = Color(0xFF8C8CFF)
private val RetroYellow = Color(0xFFFFE23F)
private val TitleColors = listOf(
    Color(0xFFFF4F3D), Color(0xFFFF9A1F), Color(0xFFFFE23F), Color(0xFF54D66A), Color(0xFF3FC8FF), Color(0xFFB66CFF),
)

/**
 * Retro console style, like the "N in 1" game carts: every app is a numbered "game" in a framed list, a pixel Atario
 * runs along the top, and the cursor ticks as it moves. N is the number of items. Text only, so nothing is decoded.
 */
@Composable
fun RetroHome(state: LauncherState) {
    val items = remember(state.items) { state.items.sortedBy { it.label.lowercase() } }
    val appContext = LocalContext.current.applicationContext
    val sounds = remember { RetroSounds(appContext) }
    SideEffect { sounds.muted = !state.arcadeSound }
    DisposableEffect(Unit) { onDispose { sounds.release() } }

    val listState = rememberLazyListState()
    val requesters = remember(items.size) { List(items.size) { FocusRequester() } }
    val scope = rememberCoroutineScope()
    var armed by remember { mutableStateOf(false) } // no tick for the focus the screen itself sets on arrival
    var lastIndex by remember { mutableStateOf(-1) }

    LaunchedEffect(state.loadedOnce, state.homeTick, items.size) {
        if (!state.loadedOnce || items.isEmpty()) return@LaunchedEffect
        armed = false
        state.focusAllApps = false
        val i = state.retroCursor.coerceIn(0, items.lastIndex)
        listState.scrollToItem(i)
        lastIndex = i
        requestFocusWhenReady(requesters[i])
        delay(400)
        armed = true
    }
    val clock = rememberClock()
    val pages = ((items.size + PAGE - 1) / PAGE).coerceAtLeast(1)
    val goToPage: (Int) -> Unit = { delta ->
        if (items.isNotEmpty()) {
            val target = (state.retroCursor + delta * PAGE).coerceIn(0, items.lastIndex)
            scope.launch {
                listState.scrollToItem(target)
                requestFocusWhenReady(requesters[target])
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .scanlines()
            .padding(top = 20.dp, bottom = 14.dp, start = ContentPad, end = ContentPad),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    text = buildAnnotatedString {
                        "${items.size} IN 1".forEachIndexed { i, c ->
                            withStyle(SpanStyle(color = TitleColors[i % TitleColors.size])) { append(c) }
                        }
                    },
                    style = HardShadow,
                    fontFamily = Mono,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 46.sp,
                    letterSpacing = 3.sp,
                    maxLines = 1,
                )
                Text("PLAYER 1  -  SELECT GAME", color = RetroBlue, fontFamily = Mono, fontSize = 12.sp, letterSpacing = 2.sp)
            }
            Spacer(Modifier.weight(1f))
            ClockButton(state) {
                Text(
                    text = "${clock.time}${if (clock.meridiem.isNotEmpty()) " " + clock.meridiem else ""}  ${weatherLine(state).uppercase()}",
                    color = RetroYellow,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        RunnerStrip(Modifier.padding(vertical = 6.dp))
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(3.dp, Color.White)
                    .padding(4.dp)
                    .border(2.dp, RetroBlue)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .onPreviewKeyEvent { e ->
                        // Remotes with page or channel keys can flip pages directly; everyone else uses the menu column.
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.ChannelUp, Key.PageUp, Key.MediaPrevious -> goToPage(-1)
                            Key.ChannelDown, Key.PageDown, Key.MediaNext -> goToPage(1)
                            else -> return@onPreviewKeyEvent false
                        }
                        true
                    },
            ) {
                if (items.isEmpty()) {
                    Text("NO GAMES", color = RetroYellow, fontFamily = Mono, fontSize = 20.sp, modifier = Modifier.align(Alignment.Center))
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                        RetroRow(
                            index = index,
                            item = item,
                            requester = requesters.getOrElse(index) { FocusRequester() },
                            onStart = {
                                sounds.play(RetroSounds.Snd.Start)
                                state.launch(item)
                            },
                            onMenu = { state.menuFor = item },
                            onFocus = {
                                val pageChanged = lastIndex >= 0 && index / PAGE != lastIndex / PAGE
                                if (armed && index != lastIndex) sounds.play(if (pageChanged) RetroSounds.Snd.Page else RetroSounds.Snd.Move)
                                lastIndex = index
                                state.retroCursor = index
                                state.focusItem(item)
                            },
                        )
                    }
                }
            }
            // The menu column. It sits beside the list, so Right from any row reaches it and Left comes back:
            // no scrolling to the ends of a long list to find Settings or Controls.
            Column(Modifier.width(178.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MENU", color = RetroBlue, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.sp)
                RetroButton("FIND GAMES") { state.searchGames() }
                RetroButton("SETTINGS") { state.openSettings() }
                RetroButton("CONTROLS") { state.showQuick = true }
                RetroButton(if (state.arcadeSound) "SOUND ON" else "SOUND OFF") { state.toggleArcadeSound() }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "PAGE ${state.retroCursor / PAGE + 1}/$pages",
                    color = RetroBlue,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
                RetroButton("PAGE UP") { goToPage(-1) }
                RetroButton("PAGE DOWN") { goToPage(1) }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RetroRow(
    index: Int,
    item: Item,
    requester: FocusRequester,
    onStart: () -> Unit,
    onMenu: () -> Unit,
    onFocus: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onStart,
        onLongClick = onMenu,
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .focusRequester(requester)
            .hoverFocus(requester)
            .mouseClick(onStart, onMenu)
            .onFocusChanged {
                focused = it.hasFocus
                if (it.hasFocus) onFocus()
            },
        shape = ClickableSurfaceDefaults.shape(RectangleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color(0x22FFFFFF),
        ),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(26.dp)) { if (focused) BlinkingCursor() }
            Text(
                text = "%03d".format(index + 1),
                color = if (focused) RetroYellow else RetroBlue,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Spacer(Modifier.width(18.dp))
            Text(
                text = item.label.uppercase(),
                style = if (focused) HardShadow else TextStyle.Default,
                color = if (focused) Color.White else Color(0xFFD8D8F8),
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The red arrow that marks the selected game, blinking like the old menus. */
@Composable
private fun BlinkingCursor() {
    val transition = rememberInfiniteTransition(label = "cursor")
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 700
                1f at 0
                1f at 349
                0f at 350
                0f at 699
            },
            RepeatMode.Restart,
        ),
        label = "blink",
    )
    Text(
        text = "►",
        color = Color(0xFFFF4F3D),
        fontSize = 18.sp,
        modifier = Modifier.graphicsLayer { alpha = blink },
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RetroButton(label: String, onClick: () -> Unit) {
    val own = remember { FocusRequester() }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().focusRequester(own).hoverFocus(own).mouseClick(onClick),
        shape = ClickableSurfaceDefaults.shape(RectangleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = RetroYellow,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
            pressedContainerColor = Color(0xFFDDDDDD),
            pressedContentColor = Color.Black,
        ),
    ) {
        Text(
            text = "[ $label ]",
            fontFamily = Mono,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}
