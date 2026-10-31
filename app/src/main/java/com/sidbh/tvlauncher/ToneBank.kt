package com.sidbh.tvlauncher

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.sin

/**
 * A set of short sound effects played through [SoundPool], the Android API made for exactly this, plus the tiny
 * synthesizer that makes them (square or sine waves and sweeps). The sounds are written as WAV files in the cache
 * folder when the bank is built and deleted when it is released, so the APK carries no audio files.
 *
 * (An earlier version used static AudioTracks, which never produced a sound on this TV.)
 */
class ToneBank(private val context: Context) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = HashMap<Any, Int>()
    private val streams = HashMap<Any, Int>()
    private val files = ArrayList<File>()
    private val ready = ConcurrentHashMap.newKeySet<Int>() // sound ids that finished loading
    private val keyOfId = ConcurrentHashMap<Int, Any>()
    private val waiting = ConcurrentHashMap.newKeySet<Any>() // played before loading finished; plays on arrival

    init {
        pool.setOnLoadCompleteListener { _, id, status ->
            if (status != 0) {
                Log.w(TAG, "load failed: $status")
                return@setOnLoadCompleteListener
            }
            ready.add(id)
            // A sound asked for while it was still loading (the Chomp intro is requested in the very first
            // frame) plays now instead of being lost.
            keyOfId[id]?.let { if (waiting.remove(it)) play(it) }
        }
    }

    fun add(key: Any, samples: ShortArray) {
        runCatching {
            val file = File.createTempFile("tone", ".wav", context.cacheDir)
            file.writeBytes(wav(samples))
            files += file
            val id = pool.load(file.path, 1)
            ids[key] = id
            keyOfId[id] = key
        }.onFailure { Log.w(TAG, "could not add sound $key", it) }
    }

    /** Plays the sound from the start; a copy of it that is still sounding is cut off first. */
    fun play(key: Any) {
        val id = ids[key] ?: return
        if (id !in ready) {
            waiting += key
            return
        }
        streams[key]?.let { pool.stop(it) }
        streams[key] = pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun stopAll() {
        for (s in streams.values) pool.stop(s)
        streams.clear()
    }

    fun release() {
        runCatching { pool.release() }
        for (f in files) f.delete()
        files.clear()
        ids.clear()
        streams.clear()
    }

    private fun wav(samples: ShortArray): ByteArray {
        val dataLen = samples.size * 2
        val b = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt(36 + dataLen); b.put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()); b.putInt(16)
        b.putShort(1); b.putShort(1) // PCM, mono
        b.putInt(RATE); b.putInt(RATE * 2); b.putShort(2); b.putShort(16)
        b.put("data".toByteArray()); b.putInt(dataLen)
        for (s in samples) b.putShort(s)
        return b.array()
    }

    companion object {
        const val RATE = 22_050
        private const val TAG = "ToneBank"

        /** A wave (square, or sine if [sine]) whose pitch follows [freq] (Hz, by seconds elapsed), fading out over its length. */
        fun synth(ms: Int, volume: Double, sine: Boolean = false, freq: (Double) -> Double): ShortArray {
            val n = RATE * ms / 1000
            val out = ShortArray(n)
            var phase = 0.0
            for (i in 0 until n) {
                val t = i.toDouble() / RATE
                phase += 2 * PI * freq(t) / RATE
                val wave = if (sine) sin(phase) else if (sin(phase) >= 0) 1.0 else -1.0
                val fade = 1.0 - i.toDouble() / n
                out[i] = (wave * volume * fade * Short.MAX_VALUE).toInt().toShort()
            }
            return out
        }

        fun sweep(ms: Int, from: Double, to: Double, volume: Double, sine: Boolean = false) =
            synth(ms, volume, sine) { t -> from + (to - from) * (t / (ms / 1000.0)) }

        /** A melody: each note is (Hz, milliseconds), with a short gap so repeated notes stay distinct. */
        fun notes(melody: List<Pair<Double, Int>>, volume: Double = 0.28, sine: Boolean = false): ShortArray {
            val parts = melody.map { (hz, ms) -> synth(ms, volume, sine) { hz } + ShortArray(RATE * 12 / 1000) }
            return parts.fold(ShortArray(0)) { acc, p -> acc + p }
        }
    }
}
