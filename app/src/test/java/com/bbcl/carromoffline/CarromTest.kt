package com.bbcl.carromoffline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

class CarromTest {
    @Test
    fun startingLayout() {
        val coins = Table.startingCoins()
        assertEquals(9, coins.count { it.kind == Kind.WHITE })
        assertEquals(9, coins.count { it.kind == Kind.BLACK })
        assertEquals(1, coins.count { it.kind == Kind.QUEEN })
        for (a in coins) for (b in coins) if (a !== b) assertTrue(hypot(a.x - b.x, a.y - b.y) >= 2 * Table.COIN_R)
        for (seat in 0..3) assertTrue(Rules.strikerFits(GameState.new(listOf(Player("a", false), Player("b", false))).coins, Table.strikerAt(seat, 0f)))
    }

    @Test
    fun straightShotPocketsCoin() {
        // A coin in front of the bottom-left pocket, struck straight along the diagonal.
        val coin = Disc(1, Kind.WHITE, 150f, 590f)
        val pocket = Table.pockets[2]
        val dir = (pocket - coin.pos).unit()
        val striker = Disc(-1, Kind.STRIKER, coin.x - dir.x * 120, coin.y - dir.y * 120)
        striker.vx = dir.x * 1500
        striker.vy = dir.y * 1500
        val sunk = mutableListOf<Kind>()
        World(listOf(coin, striker)).settle { sunk += it.kind }
        assertTrue(coin.pocketed)
        assertEquals(Kind.WHITE, sunk.first())
    }

    @Test
    fun breakShotSettlesInsideBoard() {
        val discs = Table.startingCoins().toMutableList()
        val s = Table.strikerAt(0, 0f)
        discs += Disc(-1, Kind.STRIKER, s.x, s.y).apply { vy = -Table.MAX_SPEED }
        val world = World(discs)
        world.settle()
        assertFalse(world.moving())
        for (d in discs.filter { !it.pocketed }) {
            assertTrue(d.x in 0f..Table.SIZE && d.y in 0f..Table.SIZE)
        }
        assertTrue("the pack should spread", discs.filter { it.kind != Kind.STRIKER && !it.pocketed }.any { hypot(it.x - Table.CENTER, it.y - Table.CENTER) > 120 })
    }

    @Test
    fun strikerFoulReturnsCoins() {
        var g = GameState.new(listOf(Player("A", false), Player("B", false)))
        val white = g.coins.first { it.kind == Kind.WHITE }
        // White already has one coin in; this shot sinks the striker only.
        g = g.copy(coins = g.coins.map { if (it.id == white.id) it.copy(pocketed = true) else it })
        val r = Rules.resolve(g, g.coins, emptyList(), strikerSunk = true)
        assertEquals(0, r.state.pocketed(0))
        assertEquals(1, r.state.turn)
    }

    @Test
    fun queenMustBeCovered() {
        val g = GameState.new(listOf(Player("A", false), Player("B", false)))
        val queen = g.coins.first { it.kind == Kind.QUEEN }
        val sunkQueen = g.coins.map { if (it.id == queen.id) it.copy(pocketed = true) else it }
        val pending = Rules.resolve(g, sunkQueen, listOf(queen.id), false).state
        assertEquals(Queen.PENDING, pending.queen)
        assertEquals(0, pending.turn) // queen gives another shot
        val missed = Rules.resolve(pending, pending.coins, emptyList(), false).state
        assertEquals(Queen.ON_BOARD, missed.queen)
        assertFalse(missed.coins.first { it.id == queen.id }.pocketed)
        val white = pending.coins.first { it.kind == Kind.WHITE }
        val covered = Rules.resolve(pending, pending.coins.map { if (it.id == white.id) it.copy(pocketed = true) else it }, listOf(white.id), false).state
        assertEquals(Queen.COVERED, covered.queen)
        assertEquals(0, covered.queenTeam)
    }

    /** Computer against computer, with the real physics and rules: every game should end. */
    @Test
    fun computerGamesFinish() {
        val random = Random(3)
        repeat(4) { n ->
            val players = List(if (n % 2 == 0) 2 else 4) { Player("P$it", true) }
            var g = GameState.new(players)
            var shots = 0
            while (!g.over) {
                check(++shots < 400) { "game $n did not finish" }
                val shot = Ai.wobble(Ai.plan(g, random), random)
                val discs = g.coins.filter { !it.pocketed }.map { Disc(it.id, it.kind, it.x, it.y) }.toMutableList()
                val p = Table.strikerAt(g.currentSeat, shot.t)
                discs += Disc(-1, Kind.STRIKER, p.x, p.y).apply {
                    vx = shot.dir.x * shot.power * Table.MAX_SPEED
                    vy = shot.dir.y * shot.power * Table.MAX_SPEED
                }
                val sunk = mutableListOf<Int>()
                var strikerSunk = false
                World(discs).settle(dt = World.DT) { if (it.kind == Kind.STRIKER) strikerSunk = true else sunk += it.id }
                val byId = discs.associateBy { it.id }
                val after = g.coins.map { c -> byId[c.id]?.let { c.copy(x = it.x, y = it.y, pocketed = it.pocketed) } ?: c }
                g = Rules.resolve(g, after, sunk, strikerSunk).state
            }
            println("game $n: ${players.size} players, $shots shots, team ${g.winner} won by ${g.points(g.winner)}")
            assertEquals(g, GameState.fromJson(g.toJson()))
        }
    }
}
