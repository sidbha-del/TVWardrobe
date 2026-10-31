package com.sidbh.tvlauncher.ui

import androidx.tv.material3.Border
import androidx.compose.foundation.BorderStroke
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.sidbh.tvlauncher.DayPoint
import com.sidbh.tvlauncher.HourPoint
import com.sidbh.tvlauncher.LauncherState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/** Wraps the clock and weather so a click or OK press opens the weather page. Highlights while focused. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ClockButton(state: LauncherState, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val own = remember { FocusRequester() }
    val accent = LocalStyleColors.current.accent
    val glass = LocalStyleColors.current.glass
    val open = { state.showWeather = true }
    Surface(
        onClick = open,
        modifier = modifier.focusRequester(own).hoverFocus(own).mouseClick(open),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(if (glass) 50 else 14)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = if (glass) 1.05f else 1f),
        border = if (glass) {
            ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, GlassRim), shape = RoundedCornerShape(50)),
                focusedBorder = Border(BorderStroke(1.5.dp, Color.White), shape = RoundedCornerShape(50)),
            )
        } else {
            ClickableSurfaceDefaults.border()
        },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (glass) Color(0x24FFFFFF) else Color.Transparent,
            focusedContainerColor = if (glass) Color(0x47FFFFFF) else accent.copy(alpha = 0.18f),
            pressedContainerColor = accent.copy(alpha = 0.3f),
        ),
    ) {
        Box(Modifier.padding(horizontal = if (glass) 18.dp else 10.dp, vertical = if (glass) 6.dp else 2.dp), content = content)
    }
}

/** Full-screen weather: now, the next hours, five days, and a way to switch to the current location. */
@Composable
fun WeatherScreen(state: LauncherState) {
    val scope = rememberCoroutineScope()
    val close = remember { FocusRequester() }
    var status by remember { mutableStateOf<String?>(null) }
    var editingCity by remember { mutableStateOf(false) }
    val detail = state.weatherDetail
    val colors = LocalStyleColors.current

    LaunchedEffect(Unit) { state.loadWeatherDetail() }
    LaunchedEffect(Unit) { requestFocusWhenReady(close) }

    Dialog(
        onDismissRequest = { state.showWeather = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(colors.weatherTop, colors.bgMid, colors.bgBottom)))
                .padding(horizontal = ContentPad, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (detail == null) {
                Text(
                    text = when {
                        state.cityName.isBlank() -> "No city set yet. Use your current location or enter a city."
                        status != null -> status.orEmpty()
                        else -> "Loading weather…"
                    },
                    color = TextSecondary,
                    fontSize = 20.sp,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(detail.city, color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                        Text(detail.summary, color = TextSecondary, fontSize = 20.sp)
                    }
                    Text(
                        text = "${detail.tempC}°",
                        color = TextPrimary,
                        fontSize = 96.sp,
                        fontWeight = FontWeight.ExtraLight,
                    )
                }
                Text(
                    text = "Feels like ${detail.feelsC}°   ·   Humidity ${detail.humidity}%   ·   Wind ${detail.windKmh} km/h",
                    color = TextSecondary,
                    fontSize = 17.sp,
                )
                Text("Next hours", color = colors.accent.copy(alpha = 0.7f), fontSize = 14.sp, letterSpacing = 1.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    detail.hours.forEach { HourCell(it, Modifier.weight(1f)) }
                }
                Text("Next days", color = colors.accent.copy(alpha = 0.7f), fontSize = 14.sp, letterSpacing = 1.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    detail.days.forEach { DayCell(it, Modifier.weight(1f)) }
                }
                Box(Modifier.weight(1f))
            }
            status?.let { if (detail != null) Text(it, color = TextSecondary, fontSize = 15.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Pill("Close", onClick = { state.showWeather = false }, modifier = Modifier.focusRequester(close))
                Pill("Use current location", onClick = {
                    status = "Finding your location…"
                    scope.launch {
                        status = if (state.useCurrentLocation()) null else "Couldn't find your location. Check the internet connection."
                    }
                })
                Pill("Change city", onClick = { editingCity = true })
            }
        }
    }
    if (editingCity) CityDialog(state, onDismiss = { editingCity = false })
}

@Composable
private fun HourCell(h: HourPoint, modifier: Modifier) {
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    val label = if (is24) "%02d:00".format(h.hour) else "${if (h.hour % 12 == 0) 12 else h.hour % 12} ${if (h.hour < 12) "AM" else "PM"}"
    Cell(modifier, label, "${h.tempC}°", h.summary)
}

@Composable
private fun DayCell(d: DayPoint, modifier: Modifier) {
    val day = remember(d.date) {
        runCatching { SimpleDateFormat("EEE", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d.date)!!) }
            .getOrDefault(d.date)
    }
    Cell(modifier, day.uppercase(), "${d.highC}° / ${d.lowC}°", d.summary)
}

@Composable
private fun Cell(modifier: Modifier, top: String, middle: String, bottom: String) {
    Column(
        (if (LocalStyleColors.current.glass) modifier.glass(RoundedCornerShape(22.dp)) else modifier.background(Color(0x1AFFFFFF), RoundedCornerShape(14.dp)))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(top, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(middle, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(bottom, color = TextTertiary, fontSize = 12.sp, maxLines = 1)
    }
}
