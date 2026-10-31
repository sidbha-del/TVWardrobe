package com.sidbh.tvlauncher.ui

import com.sidbh.tvlauncher.UiSounds
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.toArgb
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Text
import com.sidbh.tvlauncher.Card
import com.sidbh.tvlauncher.HomeStyle
import com.sidbh.tvlauncher.Item
import com.sidbh.tvlauncher.LauncherState
import com.sidbh.tvlauncher.RemoteArt
import com.sidbh.tvlauncher.Target
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val TileWidth = 128.dp
private val CardWidth = 192.dp

@Composable
fun LauncherRoot(state: LauncherState) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var packageTick by remember { mutableIntStateOf(0) }

    // Refresh the list when apps are installed, updated or removed.
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                state.invalidateImages()
                packageTick++
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    // Also re-read on every return to the screen, so HDMI connection state stays current.
    LaunchedEffect(packageTick, lifecycleOwner) {
        // RESUMED, not STARTED: some TCL screens (Settings) float over the launcher, which only pauses it.
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) { state.reload() }
    }
    // Navigation sounds: Classic, Glass and List Style each get their own; Chomp and Retro play theirs
    // from their own screens and use this only for the confirmation chirp.
    val appContext = context.applicationContext
    val uiSounds = remember(state.homeStyle) { UiSounds(appContext, state.homeStyle) }
    DisposableEffect(uiSounds) { onDispose { uiSounds.release() } }
    SideEffect { uiSounds.muted = !state.arcadeSound }
    val ownSounds = state.homeStyle == HomeStyle.Arcade || state.homeStyle == HomeStyle.Retro
    var soundsArmed by remember { mutableStateOf(false) } // no tick for the focus the screen sets on arrival
    LaunchedEffect(state.homeStyle, state.homeTick, state.showAll) {
        soundsArmed = false
        delay(700)
        soundsArmed = true
    }
    // Switching style plays that style's chirp (not for the very first composition, i.e. app start).
    var styleChirpReady by remember { mutableStateOf(false) }
    LaunchedEffect(uiSounds) {
        if (styleChirpReady) uiSounds.play(UiSounds.Snd.Select) else styleChirpReady = true
    }
    LaunchedEffect(state.focusKey) { if (soundsArmed && !ownSounds) uiSounds.play(UiSounds.Snd.Move) }
    LaunchedEffect(state.launchTick) { if (state.launchTick > 0 && !ownSounds) uiSounds.play(UiSounds.Snd.Select) }
    LaunchedEffect(state.arcadeSound) { if (state.arcadeSound && soundsArmed) uiSounds.play(UiSounds.Snd.Select) }
    // Every style shows the weather; only Classic has a Discover row, so only it fetches that.
    val classic = state.homeStyle == HomeStyle.Classic
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                state.refreshWeather()
                delay(30 * 60_000L)
            }
        }
    }
    // Discover only lists services whose apps are installed, so wait for the first app scan.
    LaunchedEffect(lifecycleOwner, classic) {
        if (!classic) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            snapshotFlow { state.loadedOnce }.first { it }
            while (true) {
                state.refreshContent()
                delay(60 * 60_000L)
            }
        }
    }

    CompositionLocalProvider(LocalStyleColors provides state.homeStyle.colors()) {
    TintedBackground(state) {
        when {
            state.showAll -> AllAppsScreen(state)
            state.homeStyle == HomeStyle.Glass -> GlassHome(state)
            state.homeStyle == HomeStyle.ListStyle -> ListStyleHome(state)
            state.homeStyle == HomeStyle.Arcade -> ArcadeHome(state)
            state.homeStyle == HomeStyle.Retro -> RetroHome(state)
            else -> HomeScreen(state)
        }
        state.menuFor?.let { ItemMenu(state, it) }
        if (state.showWeather) WeatherScreen(state)
        if (state.showGame) ChompGame(state)
        if (state.showQuick) QuickSettings(state)
    }
    }
}

/** Gradient whose top color follows the focused item's artwork, with a soft glow from the top left. */
@Composable
fun TintedBackground(state: LauncherState, content: @Composable BoxScope.() -> Unit) {
    // The list style shows no artwork, so it skips decoding any for the tint.
    val colors = LocalStyleColors.current
    val flat = colors.flat
    val tint by produceState(DefaultTint, state.focusKey, flat) { value = if (flat) DefaultTint else state.focusTint() }
    val top by animateColorAsState(tintTop(tint), tween(600), label = "tint")
    // `top` is read only while drawing, so focus changes redraw the gradient without recomposing.
    Box(
        Modifier.fillMaxSize().trackMouseMoves().drawBehind {
            if (flat) {
                drawRect(colors.bgBottom)
                return@drawBehind
            }
            drawRect(Brush.verticalGradient(listOf(top, colors.bgMid, colors.bgBottom)))
            drawRect(
                Brush.radialGradient(
                    colors = listOf(top.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(size.width * 0.2f, 0f),
                    radius = size.width * 0.7f,
                ),
            )
            if (colors.glass) {
                // A second, hue-shifted pool of color low on the right: glass needs a varied backdrop to read as glass.
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(top.toArgb(), hsv)
                hsv[0] = (hsv[0] + 70f) % 360f
                hsv[1] = (hsv[1] + 0.2f).coerceAtMost(0.85f)
                hsv[2] = 0.55f
                val alt = Color(android.graphics.Color.HSVToColor(hsv))
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(alt.copy(alpha = 0.5f), Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 0.95f),
                        radius = size.width * 0.6f,
                    ),
                )
            }
        },
        content = content,
    )
}

@Composable
fun HomeScreen(state: LauncherState) {
    val firstFocus = remember { FocusRequester() }
    val allAppsFocus = remember { FocusRequester() }
    LaunchedEffect(state.loadedOnce, state.homeTick) {
        if (!state.loadedOnce) return@LaunchedEffect
        if (state.focusAllApps) {
            state.focusAllApps = false
            requestFocusWhenReady(allAppsFocus)
        } else {
            requestFocusWhenReady(if (state.favorites.isNotEmpty()) firstFocus else allAppsFocus)
        }
    }

    // TV safe area: keep content off the outer ~5% where some panels crop.
    Column(Modifier.fillMaxSize().padding(top = 28.dp, bottom = 16.dp)) {
        Header(state, Modifier.padding(horizontal = ContentPad))
        Hero(state, Modifier.weight(1f).fillMaxWidth().padding(horizontal = ContentPad))
        if (state.cards.isNotEmpty()) {
            Shelf("Discover") {
                items(state.cards, key = { it.id }) { card ->
                    CardTile(
                        card = card,
                        state = state,
                        onClick = { state.openCard(card) },
                        onLongClick = { state.openCardApp(card) },
                        modifier = Modifier.width(CardWidth),
                        onFocused = { state.focusCard(card) },
                    )
                }
            }
        }
        // Down from the dock opens All Apps.
        Column(
            Modifier.onPreviewKeyEvent { event ->
                val down = event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown
                if (down) state.showAll = true
                down
            },
        ) {
            Shelf("Favorites") {
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

/** Time, am/pm marker and date, following the TV's 12/24h setting; ticks once a minute while visible. */
internal class ClockText(val time: String, val meridiem: String, val date: String)

@Composable
internal fun rememberClock(): ClockText {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var clock by remember { mutableStateOf(ClockText("", "", "")) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val is24 = DateFormat.is24HourFormat(context)
            val timeFormat = SimpleDateFormat(if (is24) "HH:mm" else "h:mm", Locale.getDefault())
            val meridiemFormat = SimpleDateFormat("a", Locale.getDefault())
            val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
            while (true) {
                val now = System.currentTimeMillis()
                val moment = Date(now)
                clock = ClockText(
                    time = timeFormat.format(moment),
                    meridiem = if (is24) "" else meridiemFormat.format(moment).uppercase(),
                    date = dateFormat.format(moment).uppercase(),
                )
                delay(60_000 - now % 60_000)
            }
        }
    }
    return clock
}

@Composable
private fun Header(state: LauncherState, modifier: Modifier = Modifier) {
    val clock = rememberClock()
    val time = clock.time
    val meridiem = clock.meridiem
    val date = clock.date

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ClockButton(state) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = time,
                    color = TextPrimary,
                    fontSize = 60.sp,
                    fontWeight = FontWeight.ExtraLight,
                    letterSpacing = (-1.5).sp,
                    maxLines = 1,
                )
                if (meridiem.isNotEmpty()) {
                    Text(
                        text = meridiem,
                        color = TextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 8.dp, top = 24.dp),
                    )
                }
                Box(Modifier.padding(horizontal = 18.dp).width(1.dp).height(38.dp).background(Color(0x33FFFFFF)))
                Column(Modifier.widthIn(max = 190.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = date,
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.6.sp,
                        maxLines = 1,
                    )
                    Text(
                        text = weatherLine(state),
                        color = TextTertiary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        TopActions(state)
    }
}

/**
 * The one top menu every style uses, so they stay consistent. Classic, Glass and List Style get Assistant,
 * Store, Settings and Controls; the two game styles (Chomp, Retro) swap Assistant and Store for Find games,
 * the only extra they need. Settings and Controls are in every style, so nobody can get stuck on a look.
 * (Accounts live in the TV's own Settings.)
 */
@Composable
internal fun TopActions(state: LauncherState, modifier: Modifier = Modifier, pillWidth: Dp? = null) {
    val gameStyle = state.homeStyle == HomeStyle.Arcade || state.homeStyle == HomeStyle.Retro
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (gameStyle) {
            Pill("Find games", onClick = { state.searchGames() }, width = pillWidth)
        } else {
            Pill("Assistant", onClick = { state.openAssistant() }, width = pillWidth)
            Pill("Store", onClick = { state.openStore() }, width = pillWidth)
        }
        Pill("Settings", onClick = { state.openSettings() }, width = pillWidth)
        Pill("Controls", onClick = { state.showQuick = true }, width = pillWidth)
    }
}

internal fun weatherLine(state: LauncherState): String {
    val weather = state.weather
    return when {
        state.cityName.isBlank() -> "Set city in Controls"
        weather == null -> "Weather unavailable"
        else -> "${weather.summary} ${weather.tempC}° · ${weather.city}"
    }
}

private data class HeroModel(val title: String, val subtitle: String, val hint: String?, val item: Item?, val card: Card?)

/** Name, details and artwork for whatever is focused; crossfades as focus moves. */
@Composable
private fun Hero(state: LauncherState, modifier: Modifier = Modifier) {
    val card = state.focusedCard
    val item = state.focused
    val model = when {
        card != null -> HeroModel(
            title = card.title,
            subtitle = "${card.source} · ${card.kind.label} · " +
                DateUtils.getRelativeTimeSpanString(card.publishedMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
            hint = if (card.appPackage != null) "OK to play   ·   Hold to open ${card.source}" else "OK to play",
            item = null,
            card = card,
        )
        item != null -> HeroModel(item.label, subtitle(item, state.lastUsedAt(item.id)), null, item, null)
        else -> null
    }

    BoxWithConstraints(modifier) {
        val artHeight = maxHeight.coerceAtMost(116.dp)
        Crossfade(targetState = model, animationSpec = tween(240), label = "hero") { m ->
            if (m != null) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = m.title,
                            color = TextPrimary,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.5).sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(text = m.subtitle, color = TextSecondary, fontSize = 15.sp, maxLines = 1)
                        if (m.hint != null) {
                            Text(text = m.hint, color = TextTertiary, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                    HeroArt(m, state, Modifier.height(artHeight).aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)))
                }
            }
        }
    }
}

@Composable
private fun HeroArt(model: HeroModel, state: LauncherState, modifier: Modifier) {
    when {
        model.item != null -> {
            val image by rememberItemImage(model.item, state)
            ItemArt(item = model.item, image = image, modifier = modifier)
        }
        model.card != null -> {
            val art by produceState<RemoteArt?>(null, model.card.id) { value = state.cardImage(model.card) }
            Box(modifier.background(TileCard)) {
                art?.let {
                    Image(
                        bitmap = it.bitmap,
                        contentDescription = model.card.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}

internal fun subtitle(item: Item, lastUsed: Long?): String {
    val used = when {
        item.target is Target.Input -> "Input"
        lastUsed != null ->
            "Opened " + DateUtils.getRelativeTimeSpanString(lastUsed, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        else -> "Not opened yet"
    }
    return "${item.category.label} · $used"
}

@Composable
internal fun Shelf(title: String, content: LazyListScope.() -> Unit) {
    Column {
        Text(
            text = title,
            color = TextSecondary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
            modifier = Modifier.padding(start = ContentPad),
        )
        val listState = rememberLazyListState()
        // Vertical content padding leaves room for the focus scale and glow, which LazyRow would clip.
        LazyRow(
            modifier = Modifier.wheelScrollsRow(listState),
            state = listState,
            contentPadding = PaddingValues(horizontal = ContentPad, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}

@Composable
internal fun ShelfTile(item: Item, state: LauncherState, surfaceModifier: Modifier) {
    AppTile(
        item = item,
        state = state,
        onClick = { state.launch(item) },
        onLongClick = { state.menuFor = item },
        modifier = Modifier.width(TileWidth),
        surfaceModifier = surfaceModifier,
        onFocused = { state.focusItem(item) },
    )
}

@Composable
internal fun AllAppsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(Modifier.width(TileWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        TileSurface(onClick = onClick, modifier = modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxSize().background(Color(0x26FFFFFF)), contentAlignment = Alignment.Center) {
                Text(
                    text = "All Apps",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp,
                )
            }
        }
        // Same height as an AppTile label, so the dock stays aligned.
        Text(text = " ", fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
    }
}
