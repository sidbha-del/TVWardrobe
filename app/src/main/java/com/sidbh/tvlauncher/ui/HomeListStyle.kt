package com.sidbh.tvlauncher.ui

import com.sidbh.tvlauncher.Target
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import com.sidbh.tvlauncher.Item
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.sidbh.tvlauncher.LauncherState

/**
 * List style: black screen, big clock, and your favorites as a plain text list ending in "All apps".
 * It never decodes an icon or banner, and fetches no weather or Discover content.
 */
@Composable
fun ListStyleHome(state: LauncherState) {
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

    Column(Modifier.fillMaxSize().padding(top = 28.dp, bottom = 16.dp, start = ContentPad, end = ContentPad)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ClockButton(state) {
                Column {
                    Text(
                        text = clock.time + if (clock.meridiem.isNotEmpty()) " " + clock.meridiem else "",
                        color = TextPrimary,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.ExtraLight,
                        letterSpacing = (-1.5).sp,
                    )
                    Text(text = clock.date, color = LocalStyleColors.current.accent, fontSize = 14.sp, letterSpacing = 1.6.sp)
                    Text(
                        text = weatherLine(state),
                        color = TextSecondary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            TopActions(state) // the same menu as Classic and Glass
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(state.favorites, key = { _, item -> item.id }) { index, item ->
                ItemListRow(
                    item = item,
                    state = state,
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
            item(key = "all-apps") {
                // Down from the last row opens All Apps.
                ListRow(
                    label = "All apps",
                    onClick = { state.showAll = true },
                    modifier = Modifier
                        .focusRequester(allAppsFocus)
                        .onPreviewKeyEvent { event ->
                            val down = event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown
                            if (down) state.showAll = true
                            down
                        },
                    dim = true,
                    reserveIcon = true,
                )
            }
        }
    }
}

/** One app as a list row: its icon, then its name. Loads only a small icon, and only while on screen. */
@Composable
internal fun ItemListRow(item: Item, state: LauncherState, modifier: Modifier = Modifier) {
    val icon by produceState<ImageBitmap?>(null, item.id) { value = state.smallIcon(item) }
    ListRow(
        label = item.label,
        onClick = { state.launch(item) },
        modifier = modifier,
        onLongClick = { state.menuFor = item },
        onFocused = { state.focusItem(item) },
        icon = icon,
        reserveIcon = true,
        leading = if (item.target is Target.Input) ({ InputIcon(item.label, Modifier.size(36.dp)) }) else null,
    )
}

private val ListIconSize = 36.dp

/**
 * One row of the list. Selection is a thin line above and below the row (and the text takes the accent color),
 * not a filled highlight. [leading] replaces the icon, for items that have no app icon (inputs).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ListRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onFocused: () -> Unit = {},
    dim: Boolean = false,
    icon: ImageBitmap? = null,
    reserveIcon: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val own = remember { FocusRequester() }
    val accent = LocalStyleColors.current.accent
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(own)
            .hoverFocus(own)
            .mouseClick(onClick, onLongClick)
            .onFocusChanged {
                focused = it.hasFocus
                if (it.hasFocus) onFocused()
            }
            .drawBehind {
                if (focused) {
                    val w = 2.dp.toPx()
                    drawLine(accent, Offset(0f, w / 2), Offset(size.width, w / 2), strokeWidth = w)
                    drawLine(accent, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), strokeWidth = w)
                }
            },
        shape = ClickableSurfaceDefaults.shape(RectangleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (dim) TextSecondary else TextPrimary,
            focusedContainerColor = Color.Transparent,
            focusedContentColor = accent,
            pressedContainerColor = accent.copy(alpha = 0.12f),
            pressedContentColor = accent,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // The same width with or without an icon keeps every name on one left edge.
            if (leading != null) {
                Box(Modifier.size(ListIconSize), contentAlignment = Alignment.Center) { leading() }
            } else if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.size(ListIconSize).clip(RoundedCornerShape(8.dp)),
                )
            } else if (reserveIcon) {
                Spacer(Modifier.size(ListIconSize))
            }
            Text(
                text = label,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A drawn icon for TV inputs, which have no app icon: an HDMI connector, AV jacks, or a screen. */
@Composable
internal fun InputIcon(label: String, modifier: Modifier = Modifier) {
    val accent = LocalStyleColors.current.accent
    Canvas(modifier) {
        val s = size.minDimension
        drawRoundRect(Color(0x26FFFFFF), cornerRadius = CornerRadius(s * 0.22f))
        val line = Stroke(width = s * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val name = label.lowercase()
        when {
            "hdmi" in name -> {
                val plug = Path().apply {
                    moveTo(s * 0.2f, s * 0.3f)
                    lineTo(s * 0.8f, s * 0.3f)
                    lineTo(s * 0.8f, s * 0.54f)
                    lineTo(s * 0.68f, s * 0.7f)
                    lineTo(s * 0.32f, s * 0.7f)
                    lineTo(s * 0.2f, s * 0.54f)
                    close()
                }
                drawPath(plug, accent, style = line)
                for (i in 0..3) {
                    val x = s * (0.34f + i * 0.107f)
                    drawLine(accent, Offset(x, s * 0.4f), Offset(x, s * 0.5f), strokeWidth = s * 0.05f, cap = StrokeCap.Round)
                }
            }
            name.startsWith("av") || "composite" in name -> {
                drawCircle(Color(0xFFFFD83A), s * 0.1f, Offset(s * 0.28f, s * 0.5f))
                drawCircle(Color.White, s * 0.1f, Offset(s * 0.5f, s * 0.5f))
                drawCircle(Color(0xFFFF4B4B), s * 0.1f, Offset(s * 0.72f, s * 0.5f))
            }
            else -> {
                drawRoundRect(accent, Offset(s * 0.2f, s * 0.26f), Size(s * 0.6f, s * 0.38f), CornerRadius(s * 0.05f), style = line)
                drawLine(accent, Offset(s * 0.4f, s * 0.76f), Offset(s * 0.6f, s * 0.76f), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
                drawLine(accent, Offset(s * 0.5f, s * 0.64f), Offset(s * 0.5f, s * 0.76f), strokeWidth = s * 0.06f)
            }
        }
    }
}
