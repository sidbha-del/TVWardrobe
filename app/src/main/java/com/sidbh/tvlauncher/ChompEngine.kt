package com.sidbh.tvlauncher

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Chomp rules with no Android or Compose dependency, so they can be unit tested. Positions are in tiles. */
enum class Dir(val dx: Int, val dy: Int) {
    Up(0, -1), Down(0, 1), Left(-1, 0), Right(1, 0), None(0, 0);

    val opposite: Dir
        get() = when (this) {
            Up -> Down
            Down -> Up
            Left -> Right
            Right -> Left
            None -> None
        }

    companion object {
        val moves = listOf(Up, Left, Down, Right)
    }
}

enum class BugMode { House, Chase, Eaten }

enum class ChompPhase { Ready, Playing, Dying, LevelClear, GameOver }

/** At tile ([tx], [ty]) heading [dir], [p] of the way (0..1) to the next tile. */
open class Mover(var tx: Int, var ty: Int) {
    var dir: Dir = Dir.None
    var p: Float = 0f
    val x: Float get() = tx + dir.dx * p
    val y: Float get() = ty + dir.dy * p
}

class Bug(val id: Int) : Mover(0, 0) {
    var mode = BugMode.House
    var frightened = false
    var releaseIn = 0f
}

object ChompMaze {
    const val W = 19
    const val H = 21
    const val TUNNEL_ROW = 9
    const val DOOR_X = 9
    const val DOOR_Y = 8

    // '#' wall, '.' dot, 'o' power pellet, 'P' Chomp start, ' ' empty floor. Row 9 wraps through the side tunnel.
    val rows = listOf(
        "###################",
        "#o.......#.......o#",
        "#.###.##.#.##.###.#",
        "#.................#",
        "#.#.##..###..##.#.#",
        "#....#...#...#....#",
        "####.### # ###.####",
        "####.#       #.####",
        "####.# ## ## #.####",
        "    .  #   #  .    ",
        "####.# ##### #.####",
        "####.#       #.####",
        "####.# ##### #.####",
        "#........#........#",
        "#.###.#.....#.###.#",
        "#o.......P.......o#",
        "#.#.....#.#.....#.#",
        "#.#####.....#####.#",
        "#.......###.......#",
        "#.................#",
        "###################",
    )

    fun isWall(x: Int, y: Int) = rows[y][x] == '#'

    /** The bug house and its door: closed to Chomp and to bugs that are out chasing. */
    fun isHouse(x: Int, y: Int) = (y == 9 && x in 8..10) || (x == DOOR_X && y == DOOR_Y)
}

class ChompEngine(var highScore: Int = 0, private val rng: Random = Random.Default) {
    val dots = Array(ChompMaze.H) { CharArray(ChompMaze.W) }
    var dotsLeft = 0
        private set
    var score = 0
        private set
    var lives = START_LIVES
        private set
    var level = 1
        private set
    var phase = ChompPhase.Ready
        private set
    var timer = 0f
        private set
    var frightTimer = 0f
        private set
    var anim = 0f
        private set

    val player = Mover(9, 15)
    var facing = Dir.Left
        private set
    private var want = Dir.None
    val bugs = List(4) { Bug(it) }

    /** Sound cues since the screen last looked; the screen drains this every frame. */
    val events = ArrayDeque<ChompEvent>()

    /** Tests switch this off to watch the bugs without Chomp dying. */
    internal var collisionsEnabled = true

    private var chain = 0
    private var extraLife = false

    private fun emit(e: ChompEvent) {
        if (events.size >= 32) events.removeFirst()
        events.addLast(e)
    }
    private val dist = IntArray(ChompMaze.W * ChompMaze.H)
    private val queue = IntArray(ChompMaze.W * ChompMaze.H)

    init {
        resetMaze()
        resetPositions()
        phase = ChompPhase.Ready
        timer = 2f
        emit(ChompEvent.Intro)
    }

    /** Remembers the direction the player wants; Chomp takes it at the next junction. */
    fun steer(d: Dir) {
        want = d
        if (phase == ChompPhase.Playing && player.dir != Dir.None && d == player.dir.opposite) reverse(player)
    }

    fun restart() {
        score = 0
        lives = START_LIVES
        level = 1
        extraLife = false
        resetMaze()
        resetPositions()
        phase = ChompPhase.Ready
        timer = 2f
        emit(ChompEvent.Intro)
    }

    fun update(dt: Float) {
        anim += dt
        when (phase) {
            ChompPhase.Ready -> {
                timer -= dt
                if (timer <= 0f) phase = ChompPhase.Playing
            }
            ChompPhase.Playing -> stepPlaying(dt)
            ChompPhase.Dying -> {
                timer -= dt
                if (timer <= 0f) {
                    if (lives > 0) {
                        lives--
                        resetPositions()
                        phase = ChompPhase.Ready
                        timer = 1.6f
                    } else {
                        phase = ChompPhase.GameOver
                    }
                }
            }
            ChompPhase.LevelClear -> {
                timer -= dt
                if (timer <= 0f) {
                    level++
                    resetMaze()
                    resetPositions()
                    phase = ChompPhase.Ready
                    timer = 2f
                    emit(ChompEvent.Intro)
                }
            }
            ChompPhase.GameOver -> Unit
        }
    }

    private fun resetMaze() {
        dotsLeft = 0
        for (y in 0 until ChompMaze.H) for (x in 0 until ChompMaze.W) {
            val c = ChompMaze.rows[y][x]
            dots[y][x] = if (c == 'P') ' ' else c
            if (c == '.' || c == 'o') dotsLeft++
        }
    }

    private fun resetPositions() {
        player.tx = 9; player.ty = 15; player.dir = Dir.None; player.p = 0f
        want = Dir.None
        facing = Dir.Left
        frightTimer = 0f
        chain = 0
        val faster = 0.4f * (level - 1)
        for (g in bugs) {
            g.dir = Dir.None; g.p = 0f; g.frightened = false
            when (g.id) {
                0 -> { g.tx = 9; g.ty = 7; g.mode = BugMode.Chase; g.dir = Dir.Left }
                1 -> { g.tx = 9; g.ty = 9; g.mode = BugMode.House; g.releaseIn = max(0.3f, 1.5f - faster) }
                2 -> { g.tx = 8; g.ty = 9; g.mode = BugMode.House; g.releaseIn = max(0.6f, 4f - faster) }
                else -> { g.tx = 10; g.ty = 9; g.mode = BugMode.House; g.releaseIn = max(0.9f, 7f - faster) }
            }
        }
    }

    private fun stepPlaying(dt: Float) {
        if (frightTimer > 0f) {
            frightTimer -= dt
            if (frightTimer <= 0f) endFright()
        }
        movePlayer(PLAYER_SPEED * dt)
        if (phase != ChompPhase.Playing) return
        for (g in bugs) moveBug(g, dt)
        checkCollisions()
    }

    // ---- Chomp ----

    private fun movePlayer(dist: Float) {
        var left = dist
        var guard = 0
        while (left > 0f && guard++ < 4) {
            if (player.dir == Dir.None) {
                choosePlayer()
                if (player.dir == Dir.None) return
            }
            val need = 1f - player.p
            if (left < need) {
                player.p += left
                return
            }
            left -= need
            player.tx = wrapX(player.tx + player.dir.dx)
            player.ty += player.dir.dy
            player.p = 0f
            facing = player.dir
            eat(player.tx, player.ty)
            if (phase != ChompPhase.Playing) return
            choosePlayer()
        }
    }

    private fun choosePlayer() {
        if (want != Dir.None && canEnter(player.tx + want.dx, player.ty + want.dy, false)) {
            player.dir = want
        } else if (player.dir == Dir.None || !canEnter(player.tx + player.dir.dx, player.ty + player.dir.dy, false)) {
            player.dir = Dir.None
            player.p = 0f
        }
    }

    private fun eat(x: Int, y: Int) {
        when (dots[y][x]) {
            '.' -> { dots[y][x] = ' '; dotsLeft--; addScore(10); emit(ChompEvent.Dot) }
            'o' -> { dots[y][x] = ' '; dotsLeft--; addScore(50); startFright(); emit(ChompEvent.Power) }
        }
        if (dotsLeft == 0) {
            phase = ChompPhase.LevelClear
            timer = 2.2f
            emit(ChompEvent.LevelClear)
        }
    }

    private fun addScore(n: Int) {
        score += n
        if (score > highScore) highScore = score
        if (!extraLife && score >= EXTRA_LIFE_AT) {
            extraLife = true
            lives++
            emit(ChompEvent.ExtraLife)
        }
    }

    // ---- bugs ----

    private fun startFright() {
        frightTimer = max(2.5f, 7f - 0.5f * (level - 1))
        chain = 0
        for (g in bugs) {
            if (g.mode == BugMode.Eaten) continue
            g.frightened = true
            if (g.mode == BugMode.Chase && g.dir != Dir.None) reverse(g)
        }
    }

    private fun endFright() {
        frightTimer = 0f
        for (g in bugs) g.frightened = false
    }

    private fun bugSpeed(g: Bug): Float {
        val k = min(1.15f, 1f + 0.04f * (level - 1))
        return when {
            g.mode == BugMode.Eaten -> 11f
            g.mode == BugMode.House -> 3.2f
            g.frightened -> 3.2f
            else -> 5f * k
        }
    }

    private fun moveBug(g: Bug, dt: Float) {
        if (g.mode == BugMode.House && g.releaseIn > 0f) {
            g.releaseIn -= dt
            return
        }
        var left = bugSpeed(g) * dt
        var guard = 0
        while (left > 0f && guard++ < 4) {
            if (g.dir == Dir.None) {
                chooseBug(g)
                if (g.dir == Dir.None) return
            }
            val need = 1f - g.p
            if (left < need) {
                g.p += left
                return
            }
            left -= need
            g.tx = wrapX(g.tx + g.dir.dx)
            g.ty += g.dir.dy
            g.p = 0f
            chooseBug(g)
        }
    }

    private fun chooseBug(g: Bug) {
        if (g.mode == BugMode.Eaten && g.tx == 9 && g.ty == 9) {
            g.mode = BugMode.House
            g.releaseIn = 0.6f
            g.dir = Dir.None
            return
        }
        if (g.mode == BugMode.House && g.tx == 9 && g.ty == 7) g.mode = BugMode.Chase
        val houseOk = g.mode != BugMode.Chase
        val options = ArrayList<Dir>(4)
        for (d in Dir.moves) {
            if (d != g.dir.opposite && canEnter(g.tx + d.dx, g.ty + d.dy, houseOk)) options += d
        }
        if (options.isEmpty()) {
            val back = g.dir.opposite
            if (back != Dir.None && canEnter(g.tx + back.dx, g.ty + back.dy, houseOk)) options += back
        }
        if (options.isEmpty()) {
            g.dir = Dir.None
            return
        }
        g.dir = when {
            g.mode == BugMode.Eaten -> bestToward(g, options, 9, 9, true)
            g.mode == BugMode.House -> bestToward(g, options, 9, 7, true)
            g.frightened -> options[rng.nextInt(options.size)]
            rng.nextFloat() < RANDOMNESS[g.id] -> options[rng.nextInt(options.size)]
            else -> {
                val (tx, ty) = target(g)
                bestToward(g, options, tx, ty, false)
            }
        }
    }

    /** Each bug hunts differently: straight at Chomp, ahead of him, a flank, or only when far away. */
    private fun target(g: Bug): Pair<Int, Int> {
        val px = player.tx
        val py = player.ty
        val d = if (player.dir != Dir.None) player.dir else facing
        var tx = px
        var ty = py
        when (g.id) {
            1 -> { tx = px + 4 * d.dx; ty = py + 4 * d.dy }
            2 -> {
                val b = bugs[0]
                tx = (px + 2 * d.dx) * 2 - b.tx
                ty = (py + 2 * d.dy) * 2 - b.ty
            }
            3 -> if (abs(g.tx - px) + abs(g.ty - py) < 8) { tx = 1; ty = 19 }
        }
        tx = tx.coerceIn(0, ChompMaze.W - 1)
        ty = ty.coerceIn(0, ChompMaze.H - 1)
        return if (canEnter(tx, ty, false)) tx to ty else px to py
    }

    /** Breadth-first distances from the target, so a bug never gets stuck on a wall between it and its goal. */
    private fun bestToward(g: Bug, options: List<Dir>, tx: Int, ty: Int, houseOk: Boolean): Dir {
        dist.fill(UNREACHABLE)
        var head = 0
        var tail = 0
        dist[ty * ChompMaze.W + tx] = 0
        queue[tail++] = ty * ChompMaze.W + tx
        while (head < tail) {
            val cur = queue[head++]
            val cx = cur % ChompMaze.W
            val cy = cur / ChompMaze.W
            for (d in Dir.moves) {
                val nx0 = cx + d.dx
                val ny = cy + d.dy
                if (!canEnter(nx0, ny, houseOk)) continue
                val nx = wrapX(nx0)
                val idx = ny * ChompMaze.W + nx
                if (dist[idx] != UNREACHABLE) continue
                dist[idx] = dist[cur] + 1
                queue[tail++] = idx
            }
        }
        var best = options[0]
        var bestD = Int.MAX_VALUE
        for (d in options) {
            val v = dist[(g.ty + d.dy) * ChompMaze.W + wrapX(g.tx + d.dx)]
            if (v < bestD) {
                bestD = v
                best = d
            }
        }
        return best
    }

    private fun checkCollisions() {
        if (!collisionsEnabled) return
        for (g in bugs) {
            if (g.mode != BugMode.Chase) continue
            val dx = g.x - player.x
            val dy = g.y - player.y
            if (dx * dx + dy * dy >= HIT_RADIUS_SQ) continue
            if (g.frightened) {
                addScore(200 shl chain)
                chain = min(chain + 1, 3)
                g.mode = BugMode.Eaten
                g.frightened = false
                emit(ChompEvent.BugEaten)
            } else {
                emit(ChompEvent.Death)
                phase = ChompPhase.Dying
                timer = 1.6f
                endFright()
                return
            }
        }
    }

    // ---- shared helpers ----

    /** Turns a mover around mid-tile without moving it. */
    private fun reverse(m: Mover) {
        m.tx = wrapX(m.tx + m.dir.dx)
        m.ty += m.dir.dy
        m.p = 1f - m.p
        m.dir = m.dir.opposite
    }

    private fun wrapX(x: Int) = if (x < 0) ChompMaze.W - 1 else if (x >= ChompMaze.W) 0 else x

    fun canEnter(x0: Int, y: Int, houseOk: Boolean): Boolean {
        if (y !in 0 until ChompMaze.H) return false
        if ((x0 < 0 || x0 >= ChompMaze.W) && y != ChompMaze.TUNNEL_ROW) return false
        val x = wrapX(x0)
        if (ChompMaze.isWall(x, y)) return false
        if (!houseOk && ChompMaze.isHouse(x, y)) return false
        return true
    }

    companion object {
        const val START_LIVES = 2
        const val PLAYER_SPEED = 5.8f
        const val EXTRA_LIFE_AT = 10_000
        private const val HIT_RADIUS_SQ = 0.36f
        private const val UNREACHABLE = 9999
        private val RANDOMNESS = floatArrayOf(0.08f, 0.15f, 0.3f, 0.2f)
    }
}
