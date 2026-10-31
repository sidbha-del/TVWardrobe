package com.sidbh.tvlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.sidbh.tvlauncher.Category
import com.sidbh.tvlauncher.Item
import com.sidbh.tvlauncher.LauncherState
import com.sidbh.tvlauncher.Target

/** Long-press menu: pin or unpin, move to another category, app info. */
@Composable
fun ItemMenu(state: LauncherState, item: Item) {
    val first = remember { FocusRequester() }
    var picking by remember { mutableStateOf(false) }
    val close = { state.menuFor = null }
    val isPinned = item.id in state.pinned

    Dialog(onDismissRequest = close) {
        Column(
            Modifier
                .width(340.dp)
                .panelSurface(RoundedCornerShape(20.dp), glassShape = RoundedCornerShape(28.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (picking) "Move ${item.label} to" else item.label,
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (picking) {
                Category.entries.forEachIndexed { index, category ->
                    MenuButton(
                        label = if (category == item.category) "${category.label} (current)" else category.label,
                        modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    ) {
                        state.setCategory(item.id, category)
                        close()
                    }
                }
            } else {
                MenuButton(
                    label = if (isPinned) "Remove from Favorites" else "Add to Favorites",
                    modifier = Modifier.focusRequester(first),
                ) {
                    state.togglePin(item.id)
                    close()
                }
                MenuButton("Move to category…") { picking = true }
                if (item.target is Target.App) {
                    MenuButton("App info") {
                        state.openAppInfo(item)
                        close()
                    }
                }
            }
        }
        // Re-run when the mode changes, because the focused button is replaced.
        LaunchedEffect(picking) { runCatching { first.requestFocus() } }
    }
}

@Composable
private fun MenuButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val own = remember { FocusRequester() }
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().focusRequester(own).hoverFocus(own).mouseClick(onClick),
        colors = styledButtonColors(),
    ) { Text(label) }
}
