package com.sidbh.tvlauncher

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class RemoteArt(val bitmap: ImageBitmap, val tint: Color)

/** Minimal thumbnail loader: no library, just a byte-sized LRU. */
class RemoteImages {
    private val cache = object : LruCache<String, RemoteArt>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: RemoteArt) = value.bitmap.width * value.bitmap.height * 4
    }

    /** Null if the download or decode fails. */
    suspend fun load(url: String): RemoteArt? {
        cache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            val bytes = download(url) ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / sample > MAX_WIDTH) sample *= 2
            val bitmap = BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: return@withContext null
            RemoteArt(bitmap.asImageBitmap(), averageColor(bitmap)).also { cache.put(url, it) }
        }
    }

    private fun download(url: String): ByteArray? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        try {
            if (conn.responseCode == 200) conn.inputStream.use { it.readBytes() } else null
        } finally {
            conn.disconnect()
        }
    } catch (_: IOException) {
        null
    }

    private companion object {
        const val MAX_WIDTH = 640
    }
}
