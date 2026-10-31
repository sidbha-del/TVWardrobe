package com.sidbh.tvlauncher

import android.content.Context

/**
 * Navigation sounds for the home styles, each with its own character: a soft click (Classic), a glassy tink
 * (Glass), a typewriter tick (List Style). Chomp and Retro play their own sounds as the focus moves, and
 * use this only for the confirmation chirp when sound effects are switched on.
 */
class UiSounds(context: Context, style: HomeStyle) {
    enum class Snd { Move, Select }

    var muted = false
    private val bank = ToneBank(context)

    init {
        when (style) {
            HomeStyle.Classic -> {
                bank.add(Snd.Move, ToneBank.synth(24, 0.20, sine = true) { 880.0 })
                bank.add(Snd.Select, ToneBank.notes(listOf(660.0 to 55, 990.0 to 90), 0.22, sine = true))
            }
            HomeStyle.Glass -> {
                bank.add(Snd.Move, ToneBank.sweep(45, 1900.0, 1500.0, 0.18, sine = true))
                bank.add(Snd.Select, ToneBank.notes(listOf(1318.5 to 60, 1760.0 to 150), 0.20, sine = true))
            }
            HomeStyle.ListStyle -> {
                bank.add(Snd.Move, ToneBank.synth(14, 0.16) { 520.0 })
                bank.add(Snd.Select, ToneBank.notes(listOf(440.0 to 45, 660.0 to 90), 0.18))
            }
            HomeStyle.Arcade, HomeStyle.Retro -> {
                bank.add(Snd.Move, ToneBank.synth(30, 0.20) { 1000.0 })
                bank.add(Snd.Select, ToneBank.notes(listOf(987.77 to 70, 1318.51 to 220), 0.24))
            }
        }
    }

    fun play(snd: Snd) {
        if (!muted) bank.play(snd)
    }

    fun release() = bank.release()
}
