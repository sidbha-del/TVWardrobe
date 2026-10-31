package com.sidbh.tvlauncher.ui

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/** Rounded text button for the top bar and the All Apps tabs. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun Pill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onFocus: (() -> Unit)? = null,
    width: Dp? = null, // equal-width buttons in a bar
) {
    val accent = LocalStyleColors.current.accent
    val content = if (selected) accent else TextSecondary
    val glass = LocalStyleColors.current.glass
    val own = remember { FocusRequester() }
    Surface(
        onClick = onClick,
        // Hover focuses (which also fires onFocus), so a mouse selects tabs like the D-pad does.
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .focusRequester(own)
            .hoverFocus(own)
            .mouseClick(onClick)
            .onFocusChanged { if (it.hasFocus) onFocus?.invoke() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = if (glass) 1.1f else 1.06f),
        border = if (glass) {
            ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, GlassRim), shape = RoundedCornerShape(50)),
                focusedBorder = Border(BorderStroke(1.5.dp, Color.White), shape = RoundedCornerShape(50)),
            )
        } else {
            ClickableSurfaceDefaults.border()
        },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) accent.copy(alpha = 0.2f) else Color.Transparent,
            contentColor = content,
            focusedContainerColor = accent,
            focusedContentColor = Color.Black,
            pressedContainerColor = accent.copy(alpha = 0.85f),
            pressedContentColor = Color.Black,
        ),
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            // Fill the width only when the button has a fixed one; otherwise each button would grab the whole row.
            modifier = (if (width != null) Modifier.fillMaxWidth() else Modifier).padding(horizontal = 16.dp, vertical = 7.dp),
        )
    }
}
