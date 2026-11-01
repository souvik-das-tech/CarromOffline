package com.bbcl.carromoffline

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class Player(val name: String, val computer: Boolean)

data class Coin(val id: Int, val kind: Kind, val x: Float, val y: Float, val pocketed: Boolean = false)

/** Where the queen is: on the board, pocketed and waiting to be covered, or covered for good. */
enum class Queen { ON_BOARD, PENDING, COVERED }

/** Team 0 plays the white coins and breaks; team 1 plays black. In a 4-player game partners sit opposite. */
fun teamKind(team: Int) = if (team == 0) Kind.WHITE else Kind.BLACK

data class GameState(
    /** 2 or 4 players, in turn order. */
    val players: List<Player>,
    val coins: List<Coin>,
    val turn: Int = 0,
    val queen: Queen = Queen.ON_BOARD,
    /** Team that pocketed (PENDING) or covered (COVERED) the queen. */
    val queenTeam: Int = -1,
    val winner: Int = -1,
) {
    val current get() = players[turn]
    val over get() = winner >= 0

    fun team(player: Int) = player % 2
    val currentTeam get() = team(turn)

    /** Seat round the board: 0 bottom, 1 right, 2 top, 3 left. Two players face each other. */
    fun seat(player: Int) = if (players.size == 2) player * 2 else player
    val currentSeat get() = seat(turn)

    fun pocketed(team: Int) = coins.count { it.kind == teamKind(team) && it.pocketed }
    fun left(team: Int) = coins.count { it.kind == teamKind(team) && !it.pocketed }
    fun teamPlayers(team: Int) = players.filterIndexed { i, _ -> team(i) == team }
    fun teamName(team: Int) = teamPlayers(team).joinToString(" & ") { it.name }

    /** Winner's points: the loser's coins still on the board, plus 3 for covering the queen. */
    fun points(team: Int) = left(1 - team) + if (queen == Queen.COVERED && queenTeam == team) 3 else 0

    fun toJson(): String = JSONObject()
        .put("players", JSONArray(players.map { JSONObject().put("name", it.name).put("computer", it.computer) }))
        .put("coins", JSONArray(coins.map {
            JSONObject().put("id", it.id).put("kind", it.kind.name).put("x", it.x.toDouble()).put("y", it.y.toDouble()).put("pocketed", it.pocketed)
        }))
        .put("turn", turn)
        .put("queen", queen.name)
        .put("queenTeam", queenTeam)
        .put("winner", winner)
        .toString()

    companion object {
        fun new(players: List<Player>) = GameState(players, Table.startingCoins().map { Coin(it.id, it.kind, it.x, it.y) })

        fun fromJson(json: String): GameState? = runCatching {
            val o = JSONObject(json)
            val p = o.getJSONArray("players")
            val c = o.getJSONArray("coins")
            GameState(
                players = List(p.length()) { p.getJSONObject(it).let { j -> Player(j.getString("name"), j.getBoolean("computer")) } },
                coins = List(c.length()) {
                    val j = c.getJSONObject(it)
                    Coin(j.getInt("id"), Kind.valueOf(j.getString("kind")), j.getDouble("x").toFloat(), j.getDouble("y").toFloat(), j.getBoolean("pocketed"))
                },
                turn = o.getInt("turn"),
                queen = Queen.valueOf(o.getString("queen")),
                queenTeam = o.getInt("queenTeam"),
                winner = o.getInt("winner"),
            )
        }.getOrNull()
    }
}

data class ShotResult(val state: GameState, val message: String)

object Rules {
    /**
     * Applies the rules after a shot has settled. [after] are the coins where they stopped, [sunk] the
     * ids of coins pocketed in this shot, and [strikerSunk] whether the striker went in too (a foul).
     */
    fun resolve(before: GameState, after: List<Coin>, sunk: List<Int>, strikerSunk: Boolean): ShotResult {
        val me = before.currentTeam
        val mine = teamKind(me)
        val name = before.current.name
        val kinds = after.associate { it.id to it.kind }
        val mineIn = sunk.filter { kinds[it] == mine }
        val queenIn = sunk.any { kinds[it] == Kind.QUEEN }
        var coins = after
        var queen = before.queen
        var queenTeam = before.queenTeam
        var again = false
        val queenId = coins.first { it.kind == Kind.QUEEN }.id
        val messages = mutableListOf<String>()

        fun returnToCentre(id: Int) {
            coins = placeNearCentre(coins, id)
        }

        if (strikerSunk) {
            // Foul: this shot's coins of your colour and the queen come back, plus one you had already pocketed.
            mineIn.forEach(::returnToCentre)
            if (queenIn || (queen == Queen.PENDING && queenTeam == me)) {
                returnToCentre(queenId)
                queen = Queen.ON_BOARD
                queenTeam = -1
            }
            coins.firstOrNull { it.kind == mine && it.pocketed && it.id !in mineIn }?.let { returnToCentre(it.id) }
            messages += "Foul! The striker went in. A coin comes back"
        } else {
            if (queen == Queen.PENDING && queenTeam == me) {
                if (mineIn.isNotEmpty()) {
                    queen = Queen.COVERED
                    messages += "Queen covered! +3"
                } else {
                    returnToCentre(queenId)
                    queen = Queen.ON_BOARD
                    queenTeam = -1
                    messages += "Queen not covered. It goes back to the centre"
                }
            }
            if (queenIn) {
                queenTeam = me
                if (mineIn.isNotEmpty()) {
                    queen = Queen.COVERED
                    messages += "Queen pocketed and covered! +3"
                } else {
                    queen = Queen.PENDING
                    messages += "Queen pocketed! Now pocket one of yours to cover it"
                }
            }
            again = mineIn.isNotEmpty() || queenIn
            if (messages.isEmpty() && mineIn.isNotEmpty()) {
                messages += if (mineIn.size == 1) "Nice shot, $name! Play again" else "${mineIn.size} coins in! Play again"
            }
        }

        var winner = -1
        val state = before.copy(coins = coins)
        when {
            state.left(1 - me) == 0 -> {
                winner = 1 - me
                messages.clear()
                messages += "$name pocketed the other side's last coin"
            }
            state.left(me) == 0 -> {
                if (queen == Queen.COVERED) {
                    winner = me
                } else {
                    // The queen must be covered before the last coin of your colour goes in.
                    returnToCentre(mineIn.lastOrNull() ?: coins.last { it.kind == mine && it.pocketed }.id)
                    messages.clear()
                    messages += "The queen must be covered before your last coin. It comes back"
                    again = false
                }
            }
        }
        if (messages.isEmpty()) messages += if (sunk.any { kinds[it] != mine }) "Wrong colour! That one counts for the other side" else "No coin in"
        val turn = if (again && winner < 0) before.turn else (before.turn + 1) % before.players.size
        return ShotResult(before.copy(coins = coins, turn = turn, queen = queen, queenTeam = queenTeam, winner = winner), messages.joinToString(". "))
    }

    /** Puts a coin back on the board as close to the centre as it fits. */
    fun placeNearCentre(coins: List<Coin>, id: Int): List<Coin> {
        val others = coins.filter { !it.pocketed && it.id != id }
        val c = Table.CENTER
        var r = 0f
        while (r < 300f) {
            val steps = if (r == 0f) 1 else (2 * PI * r / 8).toInt().coerceAtLeast(6)
            for (k in 0 until steps) {
                val a = 2 * PI * k / steps
                val x = c + r * cos(a).toFloat()
                val y = c + r * sin(a).toFloat()
                if (others.all { hypot(it.x - x, it.y - y) >= 2 * Table.COIN_R + 0.5f }) {
                    return coins.map { if (it.id == id) it.copy(x = x, y = y, pocketed = false) else it }
                }
            }
            r += 4f
        }
        return coins.map { if (it.id == id) it.copy(x = c, y = c, pocketed = false) else it }
    }

    /** True if the striker at [p] doesn't overlap any coin. */
    fun strikerFits(coins: List<Coin>, p: Vec) =
        coins.none { !it.pocketed && hypot(it.x - p.x, it.y - p.y) < Table.COIN_R + Table.STRIKER_R }
}

/** A planned shot: where on the baseline, which way, and how hard (0..1). */
data class Shot(val t: Float, val dir: Vec, val power: Float)

/** The computer player: tries many shots in a quick simulation and plays the one that works out best. */
object Ai {
    fun plan(g: GameState, random: Random = Random.Default): Shot {
        val seat = g.currentSeat
        val me = g.currentTeam
        val mine = teamKind(me)
        val live = g.coins.filter { !it.pocketed }
        val targets = live.filter { it.kind == mine || (it.kind == Kind.QUEEN && g.pocketed(me) > 0) }
        val candidates = mutableListOf<Shot>()
        val positions = (-10..10).map { it / 10f }.filter { Rules.strikerFits(g.coins, Table.strikerAt(seat, it)) }

        for (coin in targets) {
            val c = Vec(coin.x, coin.y)
            for (pocket in Table.pockets) {
                val toPocket = (pocket - c).unit()
                val ghost = c - toPocket * (Table.COIN_R + Table.STRIKER_R)
                for (t in positions) {
                    val s = Table.strikerAt(seat, t)
                    val dir = (ghost - s).unit()
                    val cut = dir.dot(toPocket)
                    if (cut < 0.35f) continue
                    // Speed the coin needs to reach the pocket, then the striker speed to give it that.
                    val coinSpeed = sqrt(2 * World.DECEL * (pocket - c).length()) * 1.35f
                    val hitSpeed = coinSpeed / (cut * (1 + World.DISC_BOUNCE) * 2.7f / 3.7f)
                    val start = sqrt(hitSpeed * hitSpeed + 2 * World.DECEL * (ghost - s).length())
                    val power = (start / Table.MAX_SPEED).coerceIn(0.25f, 1f)
                    candidates += Shot(t, dir, power)
                }
            }
        }
        // Keep the straightest cuts, plus a few plain shots at the pack in case nothing lines up.
        val best = candidates.shuffled(random).take(160)
        val fallback = positions.shuffled(random).take(6).flatMap { t ->
            val s = Table.strikerAt(seat, t)
            (targets.ifEmpty { live }).shuffled(random).take(3).map { Shot(t, (Vec(it.x, it.y) - s).unit(), 0.6f + random.nextFloat() * 0.3f) }
        }
        val all = (best + best.map { it.copy(power = (it.power * 1.25f).coerceAtMost(1f)) } + fallback)
            .ifEmpty { listOf(Shot(0f, (Vec(Table.CENTER, Table.CENTER) - Table.strikerAt(seat, 0f)).unit(), 0.9f)) }

        return all.maxBy { score(g, it) + random.nextFloat() * 5 }
    }

    fun score(g: GameState, shot: Shot): Float {
        val me = g.currentTeam
        val mine = teamKind(me)
        val discs = g.coins.filter { !it.pocketed }.map { Disc(it.id, it.kind, it.x, it.y) }.toMutableList()
        val p = Table.strikerAt(g.currentSeat, shot.t)
        val striker = Disc(-1, Kind.STRIKER, p.x, p.y)
        striker.vx = shot.dir.x * shot.power * Table.MAX_SPEED
        striker.vy = shot.dir.y * shot.power * Table.MAX_SPEED
        discs += striker
        var score = 0f
        var mineIn = 0
        var queenIn = false
        World(discs).settle(dt = 1 / 300f) { d ->
            when (d.kind) {
                mine -> { score += 100; mineIn++ }
                Kind.QUEEN -> queenIn = true
                Kind.STRIKER -> score -= 250
                else -> score -= 80
            }
        }
        if (queenIn) score += if (mineIn > 0) 160 else 40
        if (g.left(me) - mineIn == 0 && !(g.queen == Queen.COVERED || (queenIn && mineIn > 0))) score -= 300
        if (g.queen == Queen.PENDING && g.queenTeam == me && mineIn == 0) score -= 60
        // Mildly prefer leaving own coins close to pockets for the next shot.
        discs.filter { !it.pocketed && it.kind == mine }.forEach { d ->
            val near = Table.pockets.minOf { hypot(it.x - d.x, it.y - d.y) }
            score += (200 - near).coerceAtLeast(0f) / 40
        }
        return score
    }

    /** Adds a little human-like wobble to a planned shot. */
    fun wobble(shot: Shot, random: Random = Random.Default): Shot {
        val a = (random.nextFloat() - 0.5f) * 0.055f
        val d = Vec(shot.dir.x * cos(a) - shot.dir.y * sin(a), shot.dir.x * sin(a) + shot.dir.y * cos(a))
        return shot.copy(dir = d, power = (shot.power * (0.95f + random.nextFloat() * 0.1f)).coerceIn(0.1f, 1f))
    }
}
