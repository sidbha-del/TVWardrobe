package com.sidbh.tvlauncher

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color

/** Alpha-weighted mean of a 16x16 downscale, so transparent icon margins don't pull it toward black. */
fun averageColor(bitmap: Bitmap): Color {
    val small = Bitmap.createScaledBitmap(bitmap, 16, 16, true)
    var r = 0L
    var g = 0L
    var b = 0L
    var weight = 0L
    for (y in 0 until 16) for (x in 0 until 16) {
        val p = small.getPixel(x, y)
        val a = p ushr 24
        r += ((p shr 16) and 0xFF) * a
        g += ((p shr 8) and 0xFF) * a
        b += (p and 0xFF) * a
        weight += a
    }
    small.recycle()
    if (weight == 0L) return Color.Gray
    return Color((r / weight).toInt(), (g / weight).toInt(), (b / weight).toInt())
}
