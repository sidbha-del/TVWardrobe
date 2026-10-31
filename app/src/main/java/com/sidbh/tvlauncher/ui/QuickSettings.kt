package com.sidbh.tvlauncher.ui

import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.sidbh.tvlauncher.LauncherState
import kotlinx.coroutines.launch

/** Right-hand sheet of one-press system shortcuts. */
@Composable
fun QuickSettings(state: LauncherState) {
    val first = remember { FocusRequester() }
    var editingCity by remember { mutableStateOf(false) }
    // Close the sheet first, then hand over to the other screen.
    val run: (() -> Unit) -> Unit = { action ->
        state.showQuick = false
        action()
    }

    Dialog(
        onDismissRequest = { state.showQuick = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Column(
                Modifier
                    .width(380.dp)
                    .fillMaxHeight()
                    .panelSurface(RectangleShape, 0.94f, glassShape = RoundedCornerShape(topStart = 30.dp, bottomStart = 30.dp))
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Controls",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                // Switches the layout in place; the sheet stays open so the change is visible behind it.
                SheetButton("Home style", Modifier.focusRequester(first), subtitle = state.homeStyle.label) {
                    state.showStylePicker = true
                }
                SheetButton("Sound effects: ${if (state.arcadeSound) "On" else "Off"}", subtitle = "Menu sounds, Mancap and Retro") { state.toggleArcadeSound() }
                SheetButton("Wi-Fi & network") { run(state::openWifi) }
                // Opens a second sheet on top, so this one must stay open (no `run`).
                SheetButton("Bluetooth devices") { state.openBluetooth() }
                SheetButton("Weather city: ${state.cityName.ifBlank { "not set" }}") { editingCity = true }
                SheetButton("Open stock home") { run(state::openStockHome) }
            }
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }

    if (editingCity) CityDialog(state, onDismiss = { editingCity = false })
    if (state.showBluetooth) BluetoothSheet(state)
    if (state.showStylePicker) StylePicker(state)
}

/** Full-width sheet button with an optional second line; hover focuses it and a click activates it. */
@Composable
internal fun SheetButton(
    label: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    val own = remember { FocusRequester() }
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().focusRequester(own).hoverFocus(own).mouseClick(onClick),
        colors = styledButtonColors(),
    ) {
        Column {
            Text(label)
            if (subtitle != null) Text(text = subtitle, fontSize = 13.sp, modifier = Modifier.alpha(0.7f))
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun CityDialog(state: LauncherState, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(state.cityName) }
    var status by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val submit: () -> Unit = {
        scope.launch {
            status = "Looking up…"
            if (state.setCity(text)) onDismiss() else status = "Couldn't find that city, or no internet"
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(420.dp)
                .panelSurface(RoundedCornerShape(20.dp), glassShape = RoundedCornerShape(28.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Weather city", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 20.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                    .padding(14.dp)
                    .focusRequester(focus),
            )
            SheetButton("Save", onClick = submit)
            status?.let { Text(text = it, color = TextSecondary, fontSize = 14.sp) }
        }
        LaunchedEffect(Unit) {
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }
}
