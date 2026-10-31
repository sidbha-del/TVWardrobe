package com.sidbh.tvlauncher

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChompEngineTest {
    private fun playing(seed: Int = 1): ChompEngine {
        val e = ChompEngine(rng = Random(seed))
        repeat(200) { e.update(1f / 60f) } // past READY
        assertEquals(ChompPhase.Playing, e.phase)
        return e
    }

    @Test
    fun mazeIsRectangularAndHasDots() {
        assertEquals(ChompMaze.H, ChompMaze.rows.size)
        assertTrue(ChompMaze.rows.all { it.length == ChompMaze.W })
        assertEquals(167, ChompEngine().dotsLeft)
    }

    @Test
    fun playerEatsDotsMovingLeft() {
        val e = playing()
        val before = e.dotsLeft
        e.steer(Dir.Left)
        repeat(60) { e.update(1f / 60f) }
        assertTrue(e.dotsLeft < before)
        assertTrue(e.score >= 10)
    }

    @Test
    fun playerStopsAtWallsAndCanReverse() {
        val e = playing()
        e.steer(Dir.Left) // run along the open row until the wall at its end
        repeat(60 * 4) { e.update(1f / 60f) }
        assertEquals(Dir.None, e.player.dir)
        assertEquals(1, e.player.tx)
        e.steer(Dir.Right)
        repeat(30) { e.update(1f / 60f) }
        e.steer(Dir.Left)
        repeat(5) { e.update(1f / 60f) }
        assertEquals(Dir.Left, e.player.dir)
    }

    @Test
    fun bugsLeaveTheHouse() {
        val e = playing()
        e.collisionsEnabled = false
        var maxOut = 0
        repeat(20 * 60) {
            e.update(1f / 60f)
            maxOut = maxOf(maxOut, e.bugs.count { it.mode == BugMode.Chase })
        }
        assertEquals(4, maxOut)
    }

    @Test
    fun randomPlayNeverCrashesAndStaysInBounds() {
        for (seed in 1..8) {
            val rnd = Random(seed)
            val e = ChompEngine(rng = Random(seed))
            repeat(60 * 90) { i ->
                if (i % 20 == 0) e.steer(Dir.moves[rnd.nextInt(4)])
                e.update(1f / 60f)
                if (e.phase == ChompPhase.GameOver) e.restart()
                assertTrue(e.player.tx in 0 until ChompMaze.W && e.player.ty in 0 until ChompMaze.H)
                for (g in e.bugs) assertTrue(g.tx in 0 until ChompMaze.W && g.ty in 0 until ChompMaze.H)
                assertTrue(e.dotsLeft >= 0)
            }
        }
    }

    @Test
    fun bugEatenReturnsToHouseAndComesBack() {
        val e = playing()
        val g = e.bugs[0]
        // Force the situation: frightened bug on top of Chomp.
        g.frightened = true
        g.tx = e.player.tx; g.ty = e.player.ty; g.dir = Dir.None; g.p = 0f
        val before = e.score
        e.update(1f / 60f)
        assertEquals(BugMode.Eaten, g.mode)
        assertTrue(e.score >= before + 200)
        repeat(60 * 10) { e.update(1f / 60f); if (e.phase == ChompPhase.Dying) return@repeat }
        assertTrue(g.mode != BugMode.Eaten)
    }

}
