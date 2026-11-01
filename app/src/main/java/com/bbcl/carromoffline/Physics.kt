package com.bbcl.carromoffline

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

enum class Kind { WHITE, BLACK, QUEEN, STRIKER }

data class Vec(val x: Float, val y: Float) {
    operator fun plus(o: Vec) = Vec(x + o.x, y + o.y)
    operator fun minus(o: Vec) = Vec(x - o.x, y - o.y)
    operator fun times(k: Float) = Vec(x * k, y * k)
    fun dot(o: Vec) = x * o.x + y * o.y
    fun length() = hypot(x, y)
    fun unit(): Vec = length().let { if (it == 0f) Vec(0f, 0f) else Vec(x / it, y / it) }
}

/** Board measurements in millimetres, after the standard tournament board. The playing surface runs 0..SIZE. */
object Table {
    const val SIZE = 740f
    const val CENTER = SIZE / 2
    const val COIN_R = 15.9f
    const val STRIKER_R = 20.6f
    const val POCKET_R = 22.5f
    /** Distance of each pocket's centre from both walls of its corner. */
    const val POCKET_AT = 23f
    /** Distance from the wall to the line the striker's centre sits on. */
    const val BASE_FROM_EDGE = 118f
    /** How far either side of the middle the striker's centre may be placed. */
    const val BASE_HALF = 219f
    const val MAX_SPEED = 2700f

    val pockets = listOf(
        Vec(POCKET_AT, POCKET_AT), Vec(SIZE - POCKET_AT, POCKET_AT),
        Vec(POCKET_AT, SIZE - POCKET_AT), Vec(SIZE - POCKET_AT, SIZE - POCKET_AT),
    )

    /**
     * Striker position on a seat's baseline. Seats go round the board: 0 bottom, 1 right, 2 top,
     * 3 left. [t] runs -1..1 along the line, left to right or top to bottom on screen.
     */
    fun strikerAt(seat: Int, t: Float): Vec {
        val along = CENTER + t.coerceIn(-1f, 1f) * BASE_HALF
        return when (seat) {
            0 -> Vec(along, SIZE - BASE_FROM_EDGE)
            1 -> Vec(SIZE - BASE_FROM_EDGE, along)
            2 -> Vec(along, BASE_FROM_EDGE)
            else -> Vec(BASE_FROM_EDGE, along)
        }
    }

    /** The [t] whose baseline point is nearest to [p]. */
    fun tFor(seat: Int, p: Vec) = (((if (seat % 2 == 0) p.x else p.y) - CENTER) / BASE_HALF).coerceIn(-1f, 1f)

    /** Starting layout: queen in the middle, a ring of six, then a ring of twelve, colours alternating. */
    fun startingCoins(): List<Disc> {
        val coins = mutableListOf(Disc(0, Kind.QUEEN, CENTER, CENTER))
        val d = 2 * COIN_R + 0.2f
        for (k in 0 until 6) {
            val a = Math.toRadians(90.0 + 60.0 * k)
            coins += Disc(coins.size, if (k % 2 == 0) Kind.WHITE else Kind.BLACK, CENTER + d * Math.cos(a).toFloat(), CENTER - d * Math.sin(a).toFloat())
        }
        for (k in 0 until 12) {
            val a = Math.toRadians(90.0 + 30.0 * k)
            val r = if (k % 2 == 0) 2 * d else d * sqrt(3f)
            coins += Disc(coins.size, if (k % 2 == 0) Kind.BLACK else Kind.WHITE, CENTER + r * Math.cos(a).toFloat(), CENTER - r * Math.sin(a).toFloat())
        }
        return coins
    }
}

class Disc(val id: Int, val kind: Kind, var x: Float, var y: Float) {
    val r = if (kind == Kind.STRIKER) Table.STRIKER_R else Table.COIN_R
    /** The striker is about three times as heavy as a coin. */
    val m = if (kind == Kind.STRIKER) 2.7f else 1f
    var vx = 0f
    var vy = 0f
    var pocketed = false

    val pos get() = Vec(x, y)
    fun copy() = Disc(id, kind, x, y).also { it.vx = vx; it.vy = vy; it.pocketed = pocketed }
}

/** Sliding discs on the board: friction, bounces off each other and the cushions, and pockets. */
class World(val discs: List<Disc>) {
    /** Collisions so far, so the screen can buzz on hard hits. */
    var hits = 0

    fun moving() = discs.any { !it.pocketed && (it.vx != 0f || it.vy != 0f) }

    fun step(dt: Float, onPocket: (Disc) -> Unit = {}) {
        for (d in discs) {
            if (d.pocketed || (d.vx == 0f && d.vy == 0f)) continue
            d.x += d.vx * dt
            d.y += d.vy * dt
            val speed = hypot(d.vx, d.vy)
            val slower = speed - (DECEL + DRAG * speed) * dt
            if (slower <= STOP) {
                d.vx = 0f
                d.vy = 0f
            } else {
                d.vx *= slower / speed
                d.vy *= slower / speed
            }
        }
        for (i in discs.indices) {
            val a = discs[i]
            if (a.pocketed) continue
            for (j in i + 1 until discs.size) {
                val b = discs[j]
                if (!b.pocketed) collide(a, b)
            }
        }
        for (d in discs) {
            if (d.pocketed) continue
            if (Table.pockets.any { hypot(d.x - it.x, d.y - it.y) < Table.POCKET_R }) {
                d.pocketed = true
                d.vx = 0f
                d.vy = 0f
                onPocket(d)
                continue
            }
            if (d.x < d.r) { d.x = d.r; if (d.vx < 0) d.vx = -d.vx * WALL_BOUNCE }
            if (d.x > Table.SIZE - d.r) { d.x = Table.SIZE - d.r; if (d.vx > 0) d.vx = -d.vx * WALL_BOUNCE }
            if (d.y < d.r) { d.y = d.r; if (d.vy < 0) d.vy = -d.vy * WALL_BOUNCE }
            if (d.y > Table.SIZE - d.r) { d.y = Table.SIZE - d.r; if (d.vy > 0) d.vy = -d.vy * WALL_BOUNCE }
        }
    }

    private fun collide(a: Disc, b: Disc) {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val reach = a.r + b.r
        val d2 = dx * dx + dy * dy
        if (d2 >= reach * reach || d2 == 0f) return
        val d = sqrt(d2)
        val nx = dx / d
        val ny = dy / d
        // Push apart in proportion to mass so discs never sink into each other.
        val overlap = reach - d
        val share = b.m / (a.m + b.m)
        a.x -= nx * overlap * share
        a.y -= ny * overlap * share
        b.x += nx * overlap * (1 - share)
        b.y += ny * overlap * (1 - share)
        val closing = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
        if (closing >= 0) return
        val impulse = -(1 + DISC_BOUNCE) * closing / (1 / a.m + 1 / b.m)
        a.vx -= impulse / a.m * nx
        a.vy -= impulse / a.m * ny
        b.vx += impulse / b.m * nx
        b.vy += impulse / b.m * ny
        if (abs(closing) > 300f) hits++
    }

    /** Runs until everything stops (or [maxSeconds] pass). Used by the computer player to try shots. */
    fun settle(dt: Float = 1 / 360f, maxSeconds: Float = 15f, onPocket: (Disc) -> Unit = {}) {
        var t = 0f
        while (moving() && t < maxSeconds) {
            step(dt, onPocket)
            t += dt
        }
        if (moving()) discs.forEach { it.vx = 0f; it.vy = 0f }
    }

    companion object {
        const val DECEL = 950f
        const val DRAG = 0.55f
        const val STOP = 4f
        const val DISC_BOUNCE = 0.9f
        const val WALL_BOUNCE = 0.72f
        /** Physics step used on screen: small enough that a fast striker can't jump over a coin. */
        const val DT = 1 / 600f
    }
}
