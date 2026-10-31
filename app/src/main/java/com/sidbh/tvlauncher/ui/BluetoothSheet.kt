package com.sidbh.tvlauncher.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.tv.material3.Text
import com.sidbh.tvlauncher.LauncherState
import kotlinx.coroutines.launch

/** Paired Bluetooth devices, like TV Settings' list. Opening it never scans; "Pair a new device" does. */
@Composable
fun BluetoothSheet(state: LauncherState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    val pair = remember { FocusRequester() }

    // Links come and go while the sheet is open (a remote wakes up, headphones drop out).
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                scope.launch { state.refreshBluetooth() }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    Dialog(
        onDismissRequest = { state.showBluetooth = false },
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
                    text = "Bluetooth",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                state.btStatus?.let { Text(text = it, color = TextSecondary, fontSize = 14.sp) }
                if (state.btDevices.isEmpty()) {
                    Text(text = "No paired devices", color = TextSecondary, fontSize = 16.sp)
                }
                // The list takes the free height, which keeps "Pair a new device" pinned at the bottom.
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(state.btDevices, key = { _, device -> device.address }) { index, device ->
                        SheetButton(
                            label = device.name,
                            subtitle = if (device.connected) "Connected" else "Not connected",
                            modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                        ) {
                            if (!device.connected) state.connectBluetooth(device)
                        }
                    }
                }
                SheetButton("Pair a new device", Modifier.focusRequester(pair)) { state.pairNewDevice() }
            }
        }
        LaunchedEffect(Unit) {
            state.btStatus = null
            state.refreshBluetooth()
            requestFocusWhenReady(if (state.btDevices.isNotEmpty()) first else pair)
        }
    }
}
