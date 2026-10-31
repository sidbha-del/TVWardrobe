package com.sidbh.tvlauncher.ui

import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.sidbh.tvlauncher.AppImage
import com.sidbh.tvlauncher.Card
import com.sidbh.tvlauncher.Item
import com.sidbh.tvlauncher.LauncherState
import com.sidbh.tvlauncher.RemoteArt
import com.sidbh.tvlauncher.Target

private val TileShape = RoundedCornerShape(16.dp)
private val GlassTileShape = RoundedCornerShape(28.dp)

/** 16:9 rounded focusable card: scales up with a soft glow on focus, which is the Glass behaviour. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TileSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onFocusChange: (Boolean) -> Unit = {},
    focusedScale: Float = 1.08f,
    content: @Composable BoxScope.() -> Unit,
) {
    val own = remember { FocusRequester() }
    val glowColor = LocalStyleColors.current.glow
    val glass = LocalStyleColors.current.glass
    val shape = if (glass) GlassTileShape else TileShape
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        // Mouse: hover focuses, left click opens, right click is the long press.
        modifier = modifier
            .focusRequester(own)
            .hoverFocus(own)
            .mouseClick(onClick, onLongClick)
            .aspectRatio(16f / 9f)
            .onFocusChanged { onFocusChange(it.hasFocus) },
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(glowColor, if (glass) 24.dp else 18.dp)),
        // A hairline keeps light banners from bleeding into the background.
        border = if (glass) {
            ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.2.dp, GlassRim), shape = shape),
                focusedBorder = Border(BorderStroke(2.dp, Color(0xE6FFFFFF)), shape = shape),
            )
        } else {
            ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, Color(0x1FFFFFFF)), shape = TileShape),
            )
        },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent,
        ),
        content = {
            content()
            if (glass) Box(Modifier.fillMaxSize().background(GlassSheen))
        },
    )
}

/** Banners fill the card, plain icons sit centered on it, and inputs (no artwork) show their name. */
@Composable
fun ItemArt(item: Item, image: AppImage?, modifier: Modifier = Modifier) {
    val fade by animateFloatAsState(if (image != null) 1f else 0f, tween(250), label = "artFade")
    Box(modifier.background(TileCard), contentAlignment = Alignment.Center) {
        when {
            item.target is Target.Input -> Text(
                text = item.label,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp),
            )
            image == null -> Unit
            image.isBanner -> Image(
                bitmap = image.bitmap,
                contentDescription = item.label,
                modifier = Modifier.fillMaxSize().alpha(fade),
                contentScale = ContentScale.Crop,
            )
            else -> Image(
                bitmap = image.bitmap,
                contentDescription = item.label,
                modifier = Modifier.fillMaxHeight(0.6f).aspectRatio(1f).alpha(fade),
                filterQuality = FilterQuality.High,
            )
        }
    }
}

/** Discover card: thumbnail with a bottom scrim carrying the source and title, so it reads without focus. */
@Composable
fun CardTile(
    card: Card,
    state: LauncherState,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
) {
    val art by produceState<RemoteArt?>(initialValue = null, card.id) { value = state.cardImage(card) }
    val fade by animateFloatAsState(if (art != null) 1f else 0f, tween(300), label = "cardFade")

    TileSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        onFocusChange = { if (it) onFocused() },
    ) {
        Box(Modifier.fillMaxSize().background(TileCard)) {
            art?.let {
                Image(
                    bitmap = it.bitmap,
                    contentDescription = card.title,
                    modifier = Modifier.fillMaxSize().alpha(fade),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(colorStops = arrayOf(0.4f to Color.Transparent, 1f to Color(0xD9000000))),
                ),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = card.source.uppercase(),
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                    maxLines = 1,
                )
                Text(
                    text = card.title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun rememberItemImage(item: Item, state: LauncherState): State<AppImage?> =
    produceState<AppImage?>(initialValue = null, item.id) { value = state.image(item) }

/** [modifier] sizes the tile (width or fillMaxWidth); [surfaceModifier] is for a focus requester. */
@Composable
fun AppTile(
    item: Item,
    state: LauncherState,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    surfaceModifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    focusedScale: Float = 1.08f,
) {
    val image by rememberItemImage(item, state)
    var focused by remember { mutableStateOf(false) }
    val labelAlpha by animateFloatAsState(if (focused) 1f else 0f, label = "labelAlpha")
    // Glass tiles stay at full strength when not focused; the other styles dim them a little.
    val glassTiles = LocalStyleColors.current.glass
    val artAlpha by animateFloatAsState(if (focused || glassTiles) 1f else 0.82f, label = "artAlpha")

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TileSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = surfaceModifier.fillMaxWidth(),
            onFocusChange = {
                focused = it
                if (it) onFocused()
            },
            focusedScale = focusedScale,
        ) {
            ItemArt(
                item = item,
                image = image,
                modifier = Modifier.fillMaxSize().alpha(if (item.dimmed) artAlpha * 0.5f else artAlpha),
            )
        }
        Text(
            text = item.label,
            color = TextPrimary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp).alpha(labelAlpha),
        )
    }
}
