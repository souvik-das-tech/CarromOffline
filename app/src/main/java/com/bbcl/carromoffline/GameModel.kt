package com.bbcl.carromoffline

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class Phase { AIM, MOVING, SETTLING, THINKING }

/** A disc dropping into a pocket, drawn shrinking towards the pocket's centre. */
data class Falling(val kind: Kind, val from: Vec, val to: Vec, val start: Float)

/** A flash of light at a pocket when something drops in, with a label such as "+1". */
data class Burst(val at: Vec, val color: Long, val label: String, val start: Float)

/** A short message shown over the board; [id] changes for each new one so it animates in again. */
data class Toast(val id: Int, val text: String, val good: Boolean)

/** Where this phone's game comes from: just this phone, hosting a Bluetooth room, or a guest in one. */
enum class Role { LOCAL, HOST, GUEST }

/** Someone in a Bluetooth room, in seat order. The host is peer 0 and always plays first (white). */
data class Member(val peer: Int, val name: String)

/** Identifies Carrom Offline rooms, so other Bluetooth apps (and the Ludo app) never connect by mistake. */
private val ROOM_UUID: UUID = UUID.fromString("c4a7e2b9-3d58-4f1a-8e6c-5b0d9a2f7e33")

/** Runs the game: aiming, the physics while a shot plays out, the rules afterwards, computer turns and saving. */
class GameModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("carrom", Context.MODE_PRIVATE)

    // Setup screen choices, remembered between launches.
    var playerCount by mutableIntStateOf(prefs.getInt("count", 2))
    val names = mutableStateListOf(*Array(4) { prefs.getString("name_$it", "Player ${it + 1}")!! })
    val computer = mutableStateListOf(*Array(4) { prefs.getBoolean("cpu_$it", false) })

    /** A game left unfinished, which the setup screen offers to continue. */
    var savedGame by mutableStateOf(prefs.getString("game", null)?.let(GameState::fromJson)?.takeIf { !it.over })
        private set

    var game by mutableStateOf<GameState?>(null)
        private set
    var phase by mutableStateOf(Phase.AIM)
        private set
    var status by mutableStateOf("")
        private set
    var strikerT by mutableFloatStateOf(0f)
        private set
    /** The shot the computer is lining up, shown with the aiming guide. */
    var aiAim by mutableStateOf<Shot?>(null)
        private set
    /** Goes up every physics frame so the board redraws. */
    var frame by mutableLongStateOf(0L)
        private set
    /** Goes up whenever something drops into a pocket, so the screen can buzz. */
    var bumps by mutableIntStateOf(0)
        private set
    var toast by mutableStateOf<Toast?>(null)
        private set

    /** Coins and striker as they are right now; the striker is last. */
    var discs: List<Disc> = emptyList()
        private set
    val striker get() = discs.last()
    val falling = mutableListOf<Falling>()
    val bursts = mutableListOf<Burst>()
    /** Recent positions of each moving disc, newest last, for the motion trail. */
    val trails = HashMap<Int, ArrayDeque<Vec>>()
    /** Seconds of play since the shot started, for the pocket animation. */
    var clock = 0f
        private set

    private var world: World? = null
    private val sunk = mutableListOf<Int>()
    private var strikerSunk = false
    private var leftover = 0f
    private var settled: GameState? = null
    private var job: Job? = null

    // Bluetooth play.
    var role by mutableStateOf(Role.LOCAL)
        private set
    var lobbyOpen by mutableStateOf(false)
        private set
    var myName by mutableStateOf(prefs.getString("my_name", "")!!)
    /** People in the room, in player order (the host first). */
    val members = mutableStateListOf<Member>()
    /** 2 for 1 vs 1, 4 for 2 vs 2; empty seats are played by the computer. */
    var roomCount by mutableIntStateOf(2)
        private set
    /** This phone's player number in a Bluetooth game. */
    var myIndex by mutableIntStateOf(-1)
        private set
    var connecting by mutableStateOf<String?>(null)
        private set
    /** A problem to show in the lobby, such as the host leaving. */
    var netMessage by mutableStateOf<String?>(null)
    private var link: BluetoothLink? = null
    private val playerOfPeer = HashMap<Int, Int>()
    private var linkJob: Job? = null
    /** A guest's copy of the host's result, kept until the guest's own animation of the shot finishes. */
    private var pendingState: JSONObject? = null
    private var lastAimSent = 0L

    /** True if the person holding this phone plays player [index]'s shots. */
    fun isMine(index: Int): Boolean {
        val g = game ?: return false
        return if (role == Role.LOCAL) !g.players[index].computer else index == myIndex
    }

    /** The side of the board this phone's player sits at; the board is turned so it is at the bottom. */
    val viewSeat get() = game?.takeIf { role != Role.LOCAL && myIndex >= 0 }?.seat(myIndex) ?: 0

    val canAim get() = game.let { it != null && !it.over && phase == Phase.AIM && isMine(it.turn) }

    fun startNew() {
        val players = (0 until playerCount).map { Player(names[it].trim().ifEmpty { "Player ${it + 1}" }, computer[it]) }
        prefs.edit().apply {
            putInt("count", playerCount)
            for (i in 0 until 4) {
                putString("name_$i", names[i])
                putBoolean("cpu_$i", computer[i])
            }
        }.apply()
        begin(GameState.new(players))
    }

    fun playAgain() {
        if (role == Role.GUEST) return
        game?.let {
            begin(GameState.new(it.players))
            if (role == Role.HOST) sendState("", false)
        }
    }

    fun continueSaved() {
        savedGame?.let(::begin)
    }

    private fun begin(g: GameState) {
        job?.cancel()
        world = null
        game = g
        load(g)
        save(g)
        resetStriker()
        phase = Phase.AIM
        status = if (g.coins.none { it.pocketed }) "${g.current.name} breaks! Pull back to shoot" else "${g.current.name}'s turn"
        computerTurn()
    }

    /** Back to the setup screen; an unfinished game stays saved. A Bluetooth game ends for this phone. */
    fun leave() {
        job?.cancel()
        world = null
        aiAim = null
        phase = Phase.AIM
        if (role == Role.LOCAL) {
            savedGame = settled?.takeIf { !it.over }
        } else {
            closeRoom(if (role == Role.HOST) "The host ended the game" else null)
        }
        game = null
    }

    fun moveStriker(t: Float) {
        if (!canAim) return
        place(t)
        shareAim(null, final = false)
    }

    /** Called while this phone's player pulls back, so the other phones can watch the aim. */
    fun aimChanged(shot: Shot?) {
        if (canAim) shareAim(shot, final = shot == null)
    }

    fun strikerFits() = game?.let { Rules.strikerFits(it.coins, Table.strikerAt(it.currentSeat, strikerT)) } ?: false

    fun shootHuman(dir: Vec, power: Float) {
        if (!canAim) return
        if (!strikerFits()) {
            say("The striker is on a coin. Move it first", good = false)
            return
        }
        fire(dir, power)
    }

    /** Plays a shot here and tells the other phones about it. */
    private fun fire(dir: Vec, power: Float, except: Int = -1) {
        val msg = JSONObject().put("t", "shot").put("pos", strikerT.toDouble()).put("dx", dir.x.toDouble()).put("dy", dir.y.toDouble()).put("p", power.toDouble())
        when (role) {
            Role.HOST -> link?.broadcast(msg, except)
            Role.GUEST -> link?.send(0, msg)
            Role.LOCAL -> {}
        }
        shoot(dir, power)
    }

    /** Sends where the striker is and the aim being lined up, at most ~25 times a second. */
    private fun shareAim(shot: Shot?, final: Boolean) {
        val l = link ?: return
        val now = SystemClock.uptimeMillis()
        if (!final && now - lastAimSent < 40) return
        lastAimSent = now
        val msg = JSONObject().put("t", "aim").put("pos", strikerT.toDouble())
        if (shot != null) msg.put("dx", shot.dir.x.toDouble()).put("dy", shot.dir.y.toDouble()).put("p", shot.power.toDouble())
        if (role == Role.HOST) l.broadcast(msg) else l.send(0, msg)
    }

    /** Called every frame by the screen while a shot is playing out. */
    fun advance(seconds: Float) {
        val step = seconds.coerceAtMost(0.05f)
        clock += step
        frame++
        val w = world ?: return
        leftover += step
        while (leftover >= World.DT) {
            w.step(World.DT, ::dropped)
            leftover -= World.DT
        }
        for (d in discs) {
            val trail = trails.getOrPut(d.id) { ArrayDeque() }
            if (!d.pocketed && (d.vx != 0f || d.vy != 0f)) trail.addLast(d.pos) else if (trail.isNotEmpty()) trail.removeFirst()
            while (trail.size > 9) trail.removeFirst()
        }
        bursts.removeAll { clock - it.start > 0.9f }
        if (phase == Phase.MOVING && !w.moving()) {
            phase = Phase.SETTLING
            job = viewModelScope.launch {
                delay(450)
                if (role == Role.GUEST) {
                    // The host decides what the shot counted for; use its result once it is here.
                    pendingState?.let(::applyState)
                    pendingState = null
                } else {
                    settle()
                }
            }
        }
    }

    private fun load(g: GameState) {
        val p = Table.strikerAt(g.currentSeat, strikerT)
        discs = g.coins.filter { !it.pocketed }.map { Disc(it.id, it.kind, it.x, it.y) } + Disc(-1, Kind.STRIKER, p.x, p.y)
        frame++
    }

    private fun place(t: Float) {
        val g = game ?: return
        strikerT = t
        val p = Table.strikerAt(g.currentSeat, t)
        striker.x = p.x
        striker.y = p.y
        frame++
    }

    /** Puts the striker in the middle of the baseline, or the nearest spot that is clear of coins. */
    private fun resetStriker() {
        val g = game ?: return
        val t = (0..20).flatMap { listOf(it / 20f, -it / 20f) }.firstOrNull { Rules.strikerFits(g.coins, Table.strikerAt(g.currentSeat, it)) } ?: 0f
        place(t)
    }

    private fun shoot(dir: Vec, power: Float) {
        striker.vx = dir.x * power * Table.MAX_SPEED
        striker.vy = dir.y * power * Table.MAX_SPEED
        world = World(discs)
        sunk.clear()
        strikerSunk = false
        falling.clear()
        bursts.clear()
        trails.clear()
        clock = 0f
        leftover = 0f
        aiAim = null
        phase = Phase.MOVING
    }

    private fun dropped(d: Disc) {
        val pocket = Table.pockets.minBy { (it - d.pos).length() }
        falling += Falling(d.kind, d.pos, pocket, clock)
        if (d.kind == Kind.STRIKER) strikerSunk = true else sunk += d.id
        val g = game
        val (color, label) = when {
            d.kind == Kind.STRIKER -> 0xFFFF5252 to "FOUL"
            d.kind == Kind.QUEEN -> 0xFFFF6F61 to "QUEEN"
            g != null && d.kind == teamKind(g.currentTeam) -> 0xFFFFD54F to "+1"
            else -> 0xFFB0BEC5 to "+1"
        }
        bursts += Burst(pocket, color, label, clock)
        bumps++
    }

    private fun settle() {
        world = null
        val g = game ?: return
        val byId = discs.associateBy { it.id }
        val after = g.coins.map { c -> byId[c.id]?.let { c.copy(x = it.x, y = it.y, pocketed = it.pocketed) } ?: c }
        val result = Rules.resolve(g, after, sunk.toList(), strikerSunk)
        val next = result.state
        game = next
        falling.clear()
        bursts.clear()
        trails.clear()
        load(next)
        if (next.over) {
            settled = next
            if (role == Role.LOCAL) {
                prefs.edit().remove("game").apply()
                savedGame = null
            }
            status = "${next.teamName(next.winner)} won!"
            phase = Phase.AIM
            if (role == Role.HOST) sendState(result.message, false)
            return
        }
        save(next)
        resetStriker()
        status = if (next.turn == g.turn) result.message else "${result.message} · ${next.current.name}'s turn"
        val good = sunk.isNotEmpty() && !strikerSunk && next.turn == g.turn
        if (result.message != "No coin in") say(result.message, good)
        phase = Phase.AIM
        if (role == Role.HOST) sendState(result.message, good)
        computerTurn()
    }

    /** If it is a computer player's turn, works out a shot, slides the striker over, shows the aim and shoots. */
    private fun computerTurn() {
        val g = game ?: return
        if (role == Role.GUEST || g.over || !g.current.computer) return
        phase = Phase.THINKING
        job = viewModelScope.launch {
            delay(700)
            val plan = withContext(Dispatchers.Default) { Ai.plan(g) }
            val from = strikerT
            for (k in 1..24) {
                place(from + (plan.t - from) * k / 24f)
                shareAim(null, final = k == 24)
                delay(16)
            }
            val shot = Ai.wobble(plan)
            aiAim = shot
            shareAim(shot, final = true)
            frame++
            delay(800)
            fire(shot.dir, shot.power)
        }
    }

    private fun say(text: String, good: Boolean) {
        toast = Toast((toast?.id ?: 0) + 1, text, good)
    }

    // ---- Bluetooth rooms ----

    fun openLobby() {
        netMessage = null
        lobbyOpen = true
    }

    /** Leaves the lobby screen, closing any room this phone is in. */
    fun closeLobby() {
        closeRoom(if (role == Role.HOST) "The host closed the room" else null)
        lobbyOpen = false
    }

    private fun saveName() {
        myName = myName.trim().take(14)
        prefs.edit().putString("my_name", myName).apply()
    }

    private fun displayName() = myName.trim().ifEmpty { "Player" }

    fun hostRoom() {
        val adapter = bluetoothAdapter(getApplication()) ?: return
        closeRoom(null)
        saveName()
        netMessage = null
        val l = BluetoothLink(adapter, ROOM_UUID, viewModelScope)
        try {
            l.host("Carrom Offline")
        } catch (e: Exception) {
            netMessage = "Couldn't open a room: ${e.message ?: "Bluetooth error"}"
            return
        }
        link = l
        role = Role.HOST
        members.clear()
        members += Member(0, displayName())
        roomCount = 2
        myIndex = 0
        listen(l)
        pushLobby()
    }

    fun joinRoom(device: BluetoothDevice) {
        val adapter = bluetoothAdapter(getApplication()) ?: return
        closeRoom(null)
        saveName()
        netMessage = null
        val l = BluetoothLink(adapter, ROOM_UUID, viewModelScope)
        link = l
        role = Role.GUEST
        members.clear()
        connecting = device.label()
        listen(l)
        viewModelScope.launch {
            try {
                l.join(device)
                l.send(0, JSONObject().put("t", "hello").put("name", displayName()))
            } catch (e: Exception) {
                if (link === l) {
                    closeRoom(null)
                    netMessage = "Couldn't join ${device.label()}. Check that they tapped CREATE ROOM and are nearby."
                }
            } finally {
                connecting = null
            }
        }
    }

    /** Host only: 2 (1 vs 1) or 4 (2 vs 2), never fewer than the people in the room. */
    fun changeRoomCount(n: Int) {
        if (role != Role.HOST || n < members.size) return
        roomCount = n
        pushLobby()
    }

    /** Host only: starts the game, giving empty seats to the computer. */
    fun startRoom() {
        val l = link ?: return
        if (role != Role.HOST) return
        l.stopAccepting()
        playerOfPeer.clear()
        var bots = 0
        val players = (0 until roomCount).map { i ->
            val m = members.getOrNull(i)
            if (m != null) {
                playerOfPeer[m.peer] = i
                Player(m.name, computer = false)
            } else {
                Player("Computer ${++bots}", computer = true)
            }
        }
        myIndex = 0
        lobbyOpen = false
        begin(GameState.new(players))
        sendState("", false)
    }

    /** Host: tells every guest the settled game, what the shot counted for, and the next turn. */
    private fun sendState(message: String, good: Boolean) {
        val g = game ?: return
        link?.broadcast(
            JSONObject().put("t", "state").put("game", g.toJson()).put("msg", message).put("good", good)
                .put("status", status).put("pos", strikerT.toDouble()),
        )
    }

    /** Guest: shows the host's settled game. */
    private fun applyState(o: JSONObject) {
        val g = GameState.fromJson(o.getString("game")) ?: return
        val first = game == null
        job?.cancel()
        world = null
        game = g
        falling.clear()
        bursts.clear()
        trails.clear()
        load(g)
        place(o.optDouble("pos", 0.0).toFloat())
        aiAim = null
        status = o.optString("status")
        val msg = o.optString("msg")
        if (msg.isNotEmpty() && msg != "No coin in") say(msg, o.optBoolean("good"))
        phase = Phase.AIM
        if (first) lobbyOpen = false
    }

    private fun listen(l: BluetoothLink) {
        linkJob?.cancel()
        linkJob = viewModelScope.launch { l.events.collect { if (link === l) onLink(it) } }
    }

    private fun onLink(e: LinkEvent) {
        if (role == Role.HOST) onHostEvent(e) else onGuestEvent(e)
    }

    private fun onHostEvent(e: LinkEvent) {
        val l = link ?: return
        when (e) {
            is LinkEvent.Joined -> {}
            is LinkEvent.Message -> when (e.data.optString("t")) {
                "hello" -> {
                    if (game != null || members.size >= 4) {
                        l.send(e.peer, JSONObject().put("t", "full"))
                        return
                    }
                    members += Member(e.peer, e.data.optString("name").trim().take(14).ifEmpty { "Player" })
                    if (roomCount < members.size) roomCount = 4
                    pushLobby()
                }
                "aim" -> {
                    val g = game ?: return
                    if (playerOfPeer[e.peer] != g.turn || phase != Phase.AIM) return
                    showAim(e.data)
                    l.broadcast(e.data, except = e.peer)
                }
                "shot" -> {
                    val g = game ?: return
                    if (playerOfPeer[e.peer] != g.turn || phase != Phase.AIM) return
                    place(e.data.getDouble("pos").toFloat())
                    fire(Vec(e.data.getDouble("dx").toFloat(), e.data.getDouble("dy").toFloat()), e.data.getDouble("p").toFloat(), except = e.peer)
                }
            }
            is LinkEvent.Left -> {
                val g = game
                val index = playerOfPeer.remove(e.peer)
                val who = members.firstOrNull { it.peer == e.peer }
                members.removeAll { it.peer == e.peer }
                if (g == null) {
                    pushLobby()
                } else if (index != null && !g.over) {
                    // Someone walked out of range or closed the app: the computer plays on for them.
                    game = g.copy(players = g.players.mapIndexed { i, p -> if (i == index) p.copy(computer = true) else p })
                    say("${who?.name ?: "A player"} left. The computer plays for them", good = false)
                    if (g.turn == index && phase == Phase.AIM) computerTurn()
                }
            }
        }
    }

    private fun onGuestEvent(e: LinkEvent) {
        when (e) {
            is LinkEvent.Joined -> {}
            is LinkEvent.Message -> when (e.data.optString("t")) {
                "lobby" -> {
                    val list = e.data.getJSONArray("players")
                    members.clear()
                    for (k in 0 until list.length()) members += Member(k, list.getString(k))
                    roomCount = e.data.getInt("count")
                    myIndex = e.data.getInt("you")
                }
                "state" -> {
                    if (phase == Phase.MOVING) pendingState = e.data else applyState(e.data)
                }
                "aim" -> showAim(e.data)
                "shot" -> {
                    if (game == null) return
                    place(e.data.getDouble("pos").toFloat())
                    shoot(Vec(e.data.getDouble("dx").toFloat(), e.data.getDouble("dy").toFloat()), e.data.getDouble("p").toFloat())
                }
                "full" -> {
                    closeRoom(null)
                    netMessage = "That room is full or the game has already started."
                }
                "bye" -> hostGone(e.data.optString("why").ifEmpty { "The host left the game" })
            }
            is LinkEvent.Left -> if (e.peer == 0) hostGone("Lost the connection to the host")
        }
    }

    /** Shows another player's striker position and aim. */
    private fun showAim(o: JSONObject) {
        if (game == null || phase != Phase.AIM && phase != Phase.THINKING) return
        place(o.getDouble("pos").toFloat())
        aiAim = if (o.has("dx")) Shot(strikerT, Vec(o.getDouble("dx").toFloat(), o.getDouble("dy").toFloat()), o.getDouble("p").toFloat()) else null
        frame++
    }

    private fun hostGone(why: String) {
        if (role != Role.GUEST) return
        job?.cancel()
        world = null
        closeRoom(null)
        game = null
        netMessage = why
        lobbyOpen = true
    }

    /** Tells each guest who is in the room and which player number they are. */
    private fun pushLobby() {
        val l = link ?: return
        val names = JSONArray(members.map { it.name })
        members.forEachIndexed { i, m ->
            if (m.peer != 0) l.send(m.peer, JSONObject().put("t", "lobby").put("count", roomCount).put("players", names).put("you", i))
        }
    }

    /** Disconnects from the room. [goodbye], if given, is sent to the guests first. */
    private fun closeRoom(goodbye: String?) {
        val l = link
        link = null
        linkJob?.cancel()
        if (l != null) {
            if (goodbye != null) l.broadcast(JSONObject().put("t", "bye").put("why", goodbye))
            // Give the goodbye a moment to go out before the sockets close.
            viewModelScope.launch {
                delay(300)
                l.close()
            }
        }
        role = Role.LOCAL
        members.clear()
        playerOfPeer.clear()
        myIndex = -1
        connecting = null
        pendingState = null
    }

    override fun onCleared() {
        link?.close()
    }

    private fun save(g: GameState) {
        settled = g
        // Bluetooth games aren't kept: the other phones wouldn't be there to continue them.
        if (role != Role.LOCAL) return
        prefs.edit().putString("game", g.toJson()).apply()
    }
}
