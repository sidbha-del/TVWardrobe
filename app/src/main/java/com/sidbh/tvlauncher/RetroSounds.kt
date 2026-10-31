package com.sidbh.tvlauncher

import android.content.Context
/** The console menu's sounds: a tick when the cursor moves, a two-note chirp for a page turn, a coin for start. */
class RetroSounds(context: Context) {
    enum class Snd { Move, Page, Start }

    var muted = false
    private val bank = ToneBank(context)

    init {
        bank.add(Snd.Move, ToneBank.synth(34, 0.26) { 1180.0 })
        bank.add(Snd.Page, ToneBank.notes(listOf(660.0 to 45, 990.0 to 60), volume = 0.26))
        // The classic coin: a short B5 then a long E6.
        bank.add(Snd.Start, ToneBank.notes(listOf(987.77 to 70, 1318.51 to 300), volume = 0.30))
    }

    fun play(snd: Snd) {
        if (!muted) bank.play(snd)
    }

    fun release() = bank.release()
}
