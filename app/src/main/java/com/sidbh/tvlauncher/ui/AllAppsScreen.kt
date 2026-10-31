package com.sidbh.tvlauncher.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.compose.ui.graphics.Color
import com.sidbh.tvlauncher.Category
import com.sidbh.tvlauncher.HomeStyle
import com.sidbh.tvlauncher.Item
import com.sidbh.tvlauncher.LauncherState

private const val TAB_ALL = "All"

/** Opens on All: a Recent row on top, then every app A-Z. The other tabs filter by category. */
@Composable
fun AllAppsScreen(state: LauncherState) {
    BackHandler { state.closeAll() }

    val tabs = remember { listOf(TAB_ALL) + Category.entries.map { it.label } }
    var tab by remember { mutableStateOf(TAB_ALL) }

    val firstTile = remember { FocusRequester() }
    val firstTab = remember { FocusRequester() }
    // Land on the first tile, so Down from the dock needs no extra press; the tabs are one Up away.
    LaunchedEffect(Unit) {
        requestFocusWhenReady(firstTile)
        if (state.items.isEmpty()) requestFocusWhenReady(firstTab)
    }

    val items = state.items
    val recents = if (tab == TAB_ALL) state.recentAll else emptyList()
    val shown = remember(items, tab) { itemsFor(tab, items) }

    Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
        // Up from the tab row (the top of the screen) goes back to Home.
        Row(
            Modifier
                .padding(horizontal = ContentPad)
                .then(if (LocalStyleColors.current.glass) Modifier.glass(RoundedCornerShape(50)).padding(6.dp) else Modifier)
                .onPreviewKeyEvent { event ->
                    val up = event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp
                    if (up) state.closeAll()
                    up
                },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            tabs.forEachIndexed { index, label ->
                Pill(
                    label = label,
                    onClick = { tab = label },
                    modifier = if (index == 0) Modifier.focusRequester(firstTab) else Modifier,
                    selected = label == tab,
                    onFocus = { tab = label },
                    width = if (LocalStyleColors.current.glass) 120.dp else null,
                )
            }
            // An action, not a tab: it does not switch the list, it opens Play Store's search for games.
            Pill(
                label = "Find games",
                onClick = { state.searchGames() },
                width = if (LocalStyleColors.current.glass) 120.dp else null,
            )
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (tab == Category.Games.label) "No games installed yet. Use Find games in the top menu." else "Nothing here yet",
                    color = TextSecondary,
                    fontSize = 18.sp,
                )
            }
        } else {
            when (state.homeStyle) {
                HomeStyle.ListStyle -> AllAppsList(state, recents, shown, firstTile)
                HomeStyle.Glass -> AllAppsGrid(state, recents, shown, firstTile, columns = 4, focusedScale = 1.12f, showName = true)
                HomeStyle.Retro -> AllAppsList(state, recents, shown, firstTile)
                HomeStyle.Arcade -> AllAppsGrid(state, recents, shown, firstTile, columns = 5, focusedScale = 1.1f, showName = true)
                HomeStyle.Classic -> AllAppsGrid(state, recents, shown, firstTile, columns = 5, focusedScale = 1.08f, showName = false)
            }
        }
    }
}

/** Tile grid for the Classic and Glass styles; Glass is larger, lifts more and names the focused app. */
@Composable
private fun AllAppsGrid(
    state: LauncherState,
    recents: List<Item>,
    shown: List<Item>,
    firstTile: FocusRequester,
    columns: Int,
    focusedScale: Float,
    showName: Boolean,
) {
    // Recent is one row, whatever the column count, so it never leaves an orphan tile.
    val recents = recents.take(columns)
    if (showName) {
        Text(
            text = state.focused?.label.orEmpty(),
            color = TextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(start = ContentPad, top = 14.dp),
        )
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = ContentPad, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (recents.isNotEmpty()) {
            item(key = "h:recent", span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Recent") }
            items(recents, key = { "r:" + it.id }) { item ->
                GridTile(item, state, focusedScale, if (item == recents.first()) Modifier.focusRequester(firstTile) else Modifier)
            }
            item(key = "h:all", span = { GridItemSpan(maxLineSpan) }) { SectionTitle("All apps") }
        }
        items(shown, key = { "a:" + it.id }) { item ->
            GridTile(item, state, focusedScale, if (recents.isEmpty() && item == shown.first()) Modifier.focusRequester(firstTile) else Modifier)
        }
    }
}

/** List Style: icon and name per row, Recent first, then every app A-Z. */
@Composable
private fun AllAppsList(state: LauncherState, recents: List<Item>, shown: List<Item>, firstTile: FocusRequester) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = ContentPad, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (recents.isNotEmpty()) {
            item(key = "h:recent") { SectionTitle("Recent") }
            items(recents, key = { "r:" + it.id }) { item ->
                ItemListRow(item, state, if (item == recents.first()) Modifier.focusRequester(firstTile) else Modifier)
            }
            item(key = "h:all") { SectionTitle("All apps") }
        }
        items(shown, key = { "a:" + it.id }) { item ->
            ItemListRow(item, state, if (recents.isEmpty() && item == shown.first()) Modifier.focusRequester(firstTile) else Modifier)
        }
    }
}

@Composable
private fun GridTile(item: Item, state: LauncherState, focusedScale: Float, surfaceModifier: Modifier) {
    AppTile(
        item = item,
        state = state,
        onClick = { state.launch(item) },
        onLongClick = { state.menuFor = item },
        modifier = Modifier.fillMaxWidth(),
        surfaceModifier = surfaceModifier,
        onFocused = { state.focusItem(item) },
        focusedScale = focusedScale,
    )
}

@Composable
private fun SectionTitle(text: String) {
    val accent = LocalStyleColors.current.accent
    Text(
        text = text,
        // Classic keeps its neutral headings; the other styles tint them with their accent.
        color = if (LocalStyleColors.current.flat || accent != Color.White) accent else TextSecondary,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.3.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
}

private fun itemsFor(tab: String, items: List<Item>): List<Item> = when (tab) {
    TAB_ALL -> items.sortedBy { it.label.lowercase() }
    else -> items.filter { it.category.label == tab }.sortedBy { it.label.lowercase() }
}
