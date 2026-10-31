package com.sidbh.tvlauncher

import android.content.Context
import kotlin.math.PI
import kotlin.math.sin

/** What happened in the game, for the sound effects. The engine queues these; the screen plays them. */
enum class ChompEvent { Intro, Dot, Power, BugEaten, Death, LevelClear, ExtraLife }

/** Arcade-style Chomp sound effects, built from [ToneBank]. */
class ChompSounds(context: Context) {
    private enum class Snd { Intro, DotA, DotB, Power, Bug, Death, Clear, Life }

    var muted = false
    private var dotToggle = false
    private val bank = ToneBank(context)

    init {
        bank.add(Snd.Intro, intro())
        bank.add(Snd.DotA, ToneBank.sweep(70, 392.0, 523.0, 0.30))
        bank.add(Snd.DotB, ToneBank.sweep(70, 523.0, 392.0, 0.30))
        bank.add(Snd.Power, ToneBank.synth(320, 0.32) { t -> 280.0 + 160.0 * sin(2 * PI * 14 * t) })
        bank.add(Snd.Bug, ToneBank.sweep(260, 260.0, 1500.0, 0.32))
        bank.add(Snd.Death, ToneBank.synth(1300, 0.34) { t -> 900.0 * (1.0 - t / 1.35) + 40.0 * sin(2 * PI * 18 * t) + 90.0 })
        bank.add(Snd.Clear, ToneBank.notes(listOf(523.25 to 110, 659.25 to 110, 783.99 to 110, 1046.5 to 360)))
        bank.add(Snd.Life, ToneBank.notes(listOf(1318.5 to 80, 1568.0 to 80, 2093.0 to 160)))
    }

    fun play(event: ChompEvent) {
        if (muted) return
        val snd = when (event) {
            ChompEvent.Intro -> Snd.Intro
            ChompEvent.Dot -> if (dotToggle.also { dotToggle = !it }) Snd.DotA else Snd.DotB
            ChompEvent.Power -> Snd.Power
            ChompEvent.BugEaten -> Snd.Bug
            ChompEvent.Death -> Snd.Death
            ChompEvent.LevelClear -> Snd.Clear
            ChompEvent.ExtraLife -> Snd.Life
        }
        bank.play(snd)
    }

    /** Silences anything still sounding, e.g. when the game is paused or closed. */
    fun stopAll() = bank.stopAll()

    fun release() = bank.release()

    private fun intro(): ShortArray {
        val a = listOf(493.88 to 100, 987.77 to 100, 739.99 to 100, 622.25 to 100, 987.77 to 70, 739.99 to 170, 622.25 to 300)
        val b = listOf(523.25 to 100, 1046.5 to 100, 783.99 to 100, 659.25 to 100, 1046.5 to 70, 783.99 to 170, 659.25 to 300)
        return ToneBank.notes(a + b)
    }
}
