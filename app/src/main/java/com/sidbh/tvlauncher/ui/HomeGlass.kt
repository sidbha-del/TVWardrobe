package com.sidbh.tvlauncher.ui

import com.sidbh.tvlauncher.Target
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.Image
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.sidbh.tvlauncher.AppImage
import com.sidbh.tvlauncher.Item
import com.sidbh.tvlauncher.LauncherState

private val GlassTileWidth = 176.dp

/**
 * Glass style, laid out differently from Classic on purpose: a floating glass bar centered at the top with the
 * clock on the right, one large "top shelf" card showing the focused app full width, and a single row of glass tiles.
 * No Discover and no hero text column; Recent lives in All Apps.
 */
@Composable
fun GlassHome(state: LauncherState) {
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
    val clock = rememberClock()

    Column(Modifier.fillMaxSize().padding(top = 22.dp, bottom = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = ContentPad), verticalAlignment = Alignment.CenterVertically) {
            // Four equal-width buttons in one glass bar on the left, the clock capsule on the right: they cannot overlap.
            TopActions(
                state,
                Modifier.glass(RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 4.dp),
                pillWidth = 104.dp,
            )
            Spacer(Modifier.weight(1f))
            ClockButton(state) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = clock.time + if (clock.meridiem.isNotEmpty()) " " + clock.meridiem else "",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = weatherLine(state),
                        color = TextSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp).widthIn(max = 150.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        TopShelf(
            state,
            Modifier.weight(1f).fillMaxWidth().heightIn(max = 300.dp).padding(horizontal = ContentPad),
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
                itemsIndexed(state.favorites, key = { _, item -> item.id }) { index, item ->
                    GlassTile(item, state, if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                }
                item(key = "all-apps") {
                    AllAppsButton(onClick = { state.showAll = true }, modifier = Modifier.focusRequester(allAppsFocus))
                }
            }
        }
    }
}

/** The big card above the shelf: the focused app's artwork edge to edge, with its name over a soft shade. */
@Composable
private fun TopShelf(state: LauncherState, modifier: Modifier) {
    val shape = RoundedCornerShape(34.dp)
    Box(modifier.glass(shape).clip(shape)) {
        Crossfade(targetState = state.focused, animationSpec = tween(320), label = "topShelf") { item ->
            if (item != null) TopShelfCard(item, state)
        }
        Box(Modifier.fillMaxSize().background(GlassSheen))
    }
}

@Composable
private fun TopShelfCard(item: Item, state: LauncherState) {
    val image by produceState<AppImage?>(null, item.id) { value = state.image(item) }
    // Plain icons are loaded again at 384 px: the normal 192 px icon looks pixelated at this size.
    val bigIcon by produceState<ImageBitmap?>(null, item.id, image?.isBanner) {
        value = if (image?.isBanner == false) state.largeIcon(item) else null
    }
    Box(Modifier.fillMaxSize()) {
        // Backdrop: the artwork's own color, so nothing is stretched to fill the card.
        val tint = tintTop(image?.tint ?: DefaultTint)
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(tint.copy(alpha = 0.95f), Color(0xFF14102A))),
            ),
        )
        // The artwork sits in a frame at its own proportions (16:9), on the right.
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 34.dp)
                .fillMaxHeight(0.78f)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(22.dp))
                .background(TileCard),
            contentAlignment = Alignment.Center,
        ) {
            val art = image
            when {
                item.target is Target.Input -> Text(
                    text = item.label,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                art == null -> Unit
                art.isBanner -> Image(
                    bitmap = art.bitmap,
                    contentDescription = item.label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                )
                else -> Image(
                    bitmap = bigIcon ?: art.bitmap,
                    contentDescription = item.label,
                    modifier = Modifier.fillMaxHeight(0.72f).aspectRatio(1f).clip(RoundedCornerShape(18.dp)),
                    filterQuality = FilterQuality.High,
                )
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Color(0x66000000), 0.55f to Color.Transparent),
            ),
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(horizontal = 30.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.label,
                color = Color.White,
                fontSize = 38.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.5).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = subtitle(item, state.lastUsedAt(item.id)), color = Color(0xCCFFFFFF), fontSize = 15.sp, maxLines = 1)
        }
    }
}

@Composable
private fun GlassTile(item: Item, state: LauncherState, surfaceModifier: Modifier) {
    AppTile(
        item = item,
        state = state,
        onClick = { state.launch(item) },
        onLongClick = { state.menuFor = item },
        modifier = Modifier.width(GlassTileWidth),
        surfaceModifier = surfaceModifier,
        onFocused = { state.focusItem(item) },
        focusedScale = 1.12f,
    )
}
