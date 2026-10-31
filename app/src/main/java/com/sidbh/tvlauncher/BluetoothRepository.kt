package com.sidbh.tvlauncher

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

enum class BtKind { Audio, Input, Other }

data class BtDevice(val address: String, val name: String, val kind: BtKind, val connected: Boolean)

/**
 * Paired Bluetooth devices and a "connect" for them, like the list in TV Settings.
 * It never scans: only already-paired devices are read, and nothing here disconnects anything.
 *
 * Android has no public call to connect a paired device, so connecting uses the hidden
 * `connect()` of each profile by reflection. That is reachable on Android 11 for apps targeting
 * SDK 30 or lower (see targetSdk in app/build.gradle.kts); any failure just reports "couldn't".
 */
@SuppressLint("MissingPermission")
class BluetoothRepository(private val context: Context) {
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /** Blocking but quick; connected devices first, then by name. */
    fun bonded(): List<BtDevice> {
        val paired = try {
            adapter?.bondedDevices
        } catch (_: SecurityException) {
            null
        } ?: return emptyList()
        return paired.map { it.toDevice() }
            .sortedWith(compareByDescending<BtDevice> { it.connected }.thenBy { it.name.lowercase() })
    }

    private fun BluetoothDevice.toDevice(): BtDevice {
        val label = (if (Build.VERSION.SDK_INT >= 30) alias else null) ?: name ?: address
        val kind = when (bluetoothClass?.majorDeviceClass) {
            BluetoothClass.Device.Major.AUDIO_VIDEO -> BtKind.Audio
            BluetoothClass.Device.Major.PERIPHERAL -> BtKind.Input
            else -> BtKind.Other
        }
        return BtDevice(address, label, kind, isConnectedHidden())
    }

    // BluetoothDevice.isConnected() is hidden; treat "unavailable" as not connected.
    private fun BluetoothDevice.isConnectedHidden(): Boolean = try {
        BluetoothDevice::class.java.getMethod("isConnected").invoke(this) == true
    } catch (_: ReflectiveOperationException) {
        false
    } catch (_: SecurityException) {
        false
    }

    /** True if at least one profile accepted the request; the link itself comes up a moment later. */
    suspend fun connect(device: BtDevice): Boolean {
        val bt = adapter ?: return false
        val remote = try {
            bt.getRemoteDevice(device.address)
        } catch (_: IllegalArgumentException) {
            return false
        }
        val profiles = when (device.kind) {
            BtKind.Audio -> listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)
            BtKind.Input -> listOf(HID_HOST)
            BtKind.Other -> listOf(BluetoothProfile.A2DP, HID_HOST, BluetoothProfile.HEADSET)
        }
        var requested = false
        for (profile in profiles) {
            if (connectVia(bt, profile, remote)) requested = true
        }
        return requested
    }

    private suspend fun connectVia(bt: BluetoothAdapter, profile: Int, remote: BluetoothDevice): Boolean {
        val proxy = try {
            withTimeoutOrNull(PROXY_TIMEOUT_MS) { awaitProxy(bt, profile) }
        } catch (_: SecurityException) {
            null
        } ?: return false
        return try {
            withContext(Dispatchers.IO) {
                proxy.javaClass.getMethod("connect", BluetoothDevice::class.java).invoke(proxy, remote) == true
            }
        } catch (_: ReflectiveOperationException) {
            false
        } catch (_: SecurityException) {
            false
        } finally {
            bt.closeProfileProxy(profile, proxy)
        }
    }

    private suspend fun awaitProxy(bt: BluetoothAdapter, profile: Int): BluetoothProfile? =
        suspendCancellableCoroutine { cont ->
            val listener = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                    // A timeout may have cancelled us already; don't leak the proxy.
                    if (cont.isActive) cont.resume(proxy) else bt.closeProfileProxy(p, proxy)
                }

                override fun onServiceDisconnected(p: Int) {}
            }
            if (!bt.getProfileProxy(context, listener, profile)) cont.resume(null)
        }

    private companion object {
        const val HID_HOST = 4 // BluetoothProfile.HID_HOST is hidden
        const val PROXY_TIMEOUT_MS = 3_000L
    }
}
