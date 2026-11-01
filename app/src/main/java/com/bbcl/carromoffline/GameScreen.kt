package com.bbcl.carromoffline

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun GameScreen(model: GameModel) {
    val game = model.game ?: return
    var confirmLeave by remember { mutableStateOf(false) }
    BackHandler { if (game.over) model.leave() else confirmLeave = true }

    val haptic = LocalHapticFeedback.current
    LaunchedEffect(model.bumps) { if (model.bumps > 0) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }

    // Physics runs on the screen's frame clock while a shot plays out.
    LaunchedEffect(model.phase) {
        if (model.phase == Phase.MOVING || model.phase == Phase.SETTLING) {
            var last = withFrameNanos { it }
            while (true) {
                withFrameNanos {
                    model.advance((it - last) / 1e9f)
                    last = it
                }
            }
        }
    }

    // Two people facing each other: the top card is turned round for the player sitting there.
    val facing = model.role == Role.LOCAL && game.players.size == 2 && game.players.none { it.computer }
    // In a Bluetooth game this phone's team is shown at the bottom.
    val bottom = if (model.role != Role.LOCAL && model.myIndex >= 0) model.myIndex % 2 else 0
    GameBackground {
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (maxWidth > maxHeight) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TopBar(model, confirm = { confirmLeave = true })
                        Spacer(Modifier.weight(1f))
                        TeamCard(game, 1 - bottom, Modifier.fillMaxWidth())
                        TeamCard(game, bottom, Modifier.fillMaxWidth())
                        Spacer(Modifier.weight(1f))
                    }
                    Box(Modifier.fillMaxSize().weight(1.6f, fill = false), contentAlignment = Alignment.Center) {
                        CarromBoard(model)
                        ToastBanner(model, Modifier.align(Alignment.Center))
                    }
                    Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.Center) {
                        StrikerControl(model)
                    }
                }
            } else {
                Column {
                    TopBar(model, confirm = { confirmLeave = true })
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    ) {
                        TeamCard(game, 1 - bottom, Modifier.fillMaxWidth().rotate(if (facing) 180f else 0f))
                        Box(Modifier.weight(1f, fill = false).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CarromBoard(model)
                            ToastBanner(model, Modifier.align(Alignment.Center))
                        }
                        StrikerControl(model)
                        TeamCard(game, bottom, Modifier.fillMaxWidth())
                    }
                }
            }
        }
        if (game.over) {
            Confetti()
            Results(model, game)
        }
    }

    if (confirmLeave) {
        GameDialog(onDismiss = { confirmLeave = false }) {
            Text("Leave the game?", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Text(
                "Don't worry, the game is saved. Continue it any time from the menu.",
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            GlossyButton("KEEP PLAYING", GreenBrush, { confirmLeave = false }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            GlossyButton(
                "LEAVE", Brush.verticalGradient(listOf(Color(0xFFFF8A80), Color(0xFFE53935), Color(0xFFA31512))),
                { confirmLeave = false; model.leave() }, Modifier.fillMaxWidth(), height = 52.dp,
            )
        }
    }
}

@Composable
private fun TopBar(model: GameModel, confirm: () -> Unit) {
    val game = model.game ?: return
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(44.dp)
                .shadow(6.dp, CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF2C6E6A), Color(0xFF123A40))), CircleShape)
                .border(1.5.dp, Teal.copy(alpha = 0.6f), CircleShape)
                .clickable { if (game.over) model.leave() else confirm() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Menu", tint = Color.White)
        }
        Spacer(Modifier.weight(1f))
        GoldText("CARROM", 24, spacing = 3f)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .height(32.dp)
                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(50))
                .border(1.dp, GlassLine, RoundedCornerShape(50))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (game.players.size == 2) "1 VS 1" else "2 VS 2", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
    }
}

/** Shot messages ("Nice shot!", "Foul!") pop up over the board for a moment. */
@Composable
private fun ToastBanner(model: GameModel, modifier: Modifier) {
    val toast = model.toast
    var shown by remember { mutableStateOf<Toast?>(null) }
    LaunchedEffect(toast?.id) {
        if (toast == null) return@LaunchedEffect
        shown = toast
        delay(1900)
        shown = null
    }
    // Keep the last message while it fades out.
    var last by remember { mutableStateOf<Toast?>(null) }
    if (shown != null) last = shown
    AnimatedVisibility(
        shown != null,
        modifier = modifier,
        enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = 500f), initialScale = 0.6f) + fadeIn(),
        exit = fadeOut(tween(250)) + scaleOut(targetScale = 0.9f),
    ) {
        val t = last ?: return@AnimatedVisibility
        val shape = RoundedCornerShape(50)
        Text(
            t.text,
            color = if (t.good) Ink else Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .shadow(16.dp, shape)
                .background(
                    if (t.good) GoldBrush else Brush.verticalGradient(listOf(Color(0xFF3A4A52), Color(0xFF1A262C))),
                    shape,
                )
                .border(2.dp, Color.White.copy(alpha = 0.6f), shape)
                .padding(horizontal = 22.dp, vertical = 12.dp),
        )
    }
}

/** Wooden rail to slide the striker along the baseline, with a hint; or what the computer is doing. */
@Composable
private fun StrikerControl(model: GameModel) {
    val game = model.game ?: return
    Column(Modifier.fillMaxWidth().height(78.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        when {
            game.over -> {}
            model.canAim -> {
                // On a board turned for the right or top seat, the baseline runs the other way on screen.
                val flip = model.viewSeat == 1 || model.viewSeat == 2
                StrikerRail(if (flip) -model.strikerT else model.strikerT, { model.moveStriker(if (flip) -it else it) }, Modifier.fillMaxWidth().padding(horizontal = 8.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    "Touch the striker and pull back to shoot · use the rail to move it",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
            !model.isMine(game.turn) && model.phase != Phase.MOVING && model.phase != Phase.SETTLING -> {
                val dots by rememberInfiniteTransition(label = "dots").animateFloat(
                    0f, 3.99f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "dots",
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (game.current.computer) Icons.Default.SmartToy else Icons.Default.Person, null, tint = Teal, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${game.current.name} is aiming" + ".".repeat(dots.toInt()),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** A grooved wooden rail with the striker as its handle. [t] runs -1..1. */
@Composable
private fun StrikerRail(t: Float, onChange: (Float) -> Unit, modifier: Modifier) {
    val update by rememberUpdatedState(onChange)
    Canvas(
        modifier
            .height(46.dp)
            .pointerInput(Unit) {
                val pad = size.height / 2f
                fun at(x: Float) = ((x - pad) / (size.width - 2 * pad) * 2 - 1).coerceIn(-1f, 1f)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    update(at(down.position.x))
                    drag(down.id) {
                        update(at(it.position.x))
                        it.consume()
                    }
                }
            },
    ) {
        val h = size.height
        val pad = h / 2
        val rail = Size(size.width, h * 0.46f)
        val top = (h - rail.height) / 2
        val radius = CornerRadius(rail.height / 2)
        drawRoundRect(Color.Black.copy(alpha = 0.4f), Offset(0f, top + 3), rail, radius)
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFF8A4A22), Color(0xFF5A2410), Color(0xFF3A1508)), top, top + rail.height),
            Offset(0f, top), rail, radius,
        )
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color(0xFF2A1006)), top + rail.height * 0.3f, top + rail.height * 0.7f),
            Offset(pad, top + rail.height * 0.32f), Size(size.width - 2 * pad, rail.height * 0.36f), CornerRadius(rail.height),
        )
        drawRoundRect(Brass, Offset(0f, top), rail, radius, style = Stroke(2.dp.toPx()))
        for (k in listOf(-1f, 1f)) {
            drawCircle(Color(0xFFC62828), rail.height * 0.22f, Offset(pad + (k + 1) / 2 * (size.width - 2 * pad), h / 2))
        }
        drawCoin(Offset(pad + (t + 1) / 2 * (size.width - 2 * pad), h / 2), h * 0.46f, Kind.STRIKER)
    }
}

/** A team's card: avatars, names, a tray of the coins pocketed so far, the queen, and the count. */
@Composable
private fun TeamCard(game: GameState, team: Int, modifier: Modifier) {
    val active = !game.over && game.currentTeam == team
    val shape = RoundedCornerShape(22.dp)
    val kind = teamKind(team)
    val glow by animateFloatAsState(if (active) 1f else 0f, tween(400), label = "glow")
    Row(
        modifier
            .height(76.dp)
            .shadow((14 * glow).dp, shape, ambientColor = Teal, spotColor = Teal)
            .background(
                Brush.horizontalGradient(
                    if (active) listOf(Color(0xFF1F6B66), Color(0xFF103C40), Color(0xFF0C2C30))
                    else listOf(Color(0xFF16302F), Color(0xFF0E2224)),
                ),
                shape,
            )
            .border(if (active) 2.dp else 1.dp, if (active) Teal else GlassLine, shape)
            .padding(start = 8.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val members = game.players.indices.filter { game.team(it) == team }
        Box(Modifier.width((56 + 26 * (members.size - 1)).dp)) {
            members.forEachIndexed { k, i ->
                Avatar(game.players[i], kind, active && game.turn == i, Modifier.padding(start = (26 * k).dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    game.teamName(team),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (active) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "TURN",
                        color = Ink,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        modifier = Modifier.background(GoldBrush, RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            // Tray of pocketed coins.
            Row(
                Modifier
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val count = game.pocketed(team)
                repeat(9) {
                    if (it < count) CoinIcon(kind, Modifier.size(14.dp))
                    else Box(Modifier.size(14.dp).padding(2.dp).background(Color.White.copy(alpha = 0.08f), CircleShape))
                }
                if (game.queenTeam == team) {
                    Spacer(Modifier.width(4.dp))
                    CoinIcon(Kind.QUEEN, Modifier.size(16.dp))
                    Text(
                        if (game.queen == Queen.COVERED) " +3" else " cover!",
                        color = if (game.queen == Queen.COVERED) Gold else Color(0xFFFF8A80),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${game.pocketed(team)}",
                style = TextStyle(
                    brush = if (active) GoldBrush else Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.7f))),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                ),
            )
            Text("/ 9", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = (-4).dp))
        }
    }
}

/** Round avatar with the player's initial (or a robot), a coin badge, and a spinning ring on their turn. */
@Composable
private fun Avatar(player: Player, kind: Kind, active: Boolean, modifier: Modifier) {
    val spin by rememberInfiniteTransition(label = "ring").animateFloat(
        0f, 360f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "spin",
    )
    Box(modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            if (active) {
                rotate(spin) {
                    drawCircle(Brush.sweepGradient(listOf(Teal, Gold, Color.Transparent, Teal)), style = Stroke(3.5.dp.toPx()))
                }
            } else {
                drawCircle(Color.White.copy(alpha = 0.15f), style = Stroke(2.dp.toPx()))
            }
        }
        val palette = if (kind == Kind.WHITE) listOf(Color(0xFFFFE0B2), Color(0xFFE08A3C), Color(0xFF8E3E12))
        else listOf(Color(0xFF90A4AE), Color(0xFF455A64), Color(0xFF1C2A30))
        Box(
            Modifier
                .size(46.dp)
                .shadow(4.dp, CircleShape)
                .background(Brush.linearGradient(palette), CircleShape)
                .border(2.dp, Brush.verticalGradient(listOf(Color(0xFFFFE89A), Color(0xFFB87A10))), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (player.computer) Icon(Icons.Default.SmartToy, "Computer", tint = Color.White, modifier = Modifier.size(24.dp))
            else Text(player.name.take(1).uppercase(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
        CoinIcon(kind, Modifier.size(20.dp).align(Alignment.BottomEnd))
    }
}

/** Dark gradient card in a dialog, shared by the leave and results dialogs. */
@Composable
private fun GameDialog(onDismiss: () -> Unit, dismissible: Boolean = true, content: @Composable () -> Unit) {
    Dialog(onDismiss, DialogProperties(dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible)) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .shadow(24.dp, shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF1F5E5A), Color(0xFF103538), Color(0xFF071A1C))), shape)
                .border(2.dp, Brush.verticalGradient(listOf(Color(0xFFFFE08A), Color(0xFFB87A10))), shape)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

@Composable
private fun Results(model: GameModel, game: GameState) {
    val winner = game.winner
    GameDialog(onDismiss = {}, dismissible = false) {
        Box(
            Modifier
                .size(88.dp)
                .shadow(16.dp, CircleShape, spotColor = Gold)
                .background(GoldBrush, CircleShape)
                .border(3.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.EmojiEvents, null, tint = Color.White, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(12.dp))
        GoldText("WINNER!", 34)
        Text(game.teamName(winner), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        for (team in listOf(winner, 1 - winner)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(Color.White.copy(alpha = if (team == winner) 0.14f else 0.07f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoinIcon(teamKind(team), Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(game.teamName(team), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${game.pocketed(team)} of 9 coins pocketed", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
                if (team == winner) {
                    Text("+${game.points(team)}", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Text(
            "Points: the other side's coins left on the board, plus 3 for covering the queen.",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(18.dp))
        if (model.role == Role.GUEST) {
            Text("The host can start another game", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        } else {
            GlossyButton("PLAY AGAIN", GoldBrush, model::playAgain, Modifier.fillMaxWidth(), icon = Icons.Default.Refresh, textColor = Ink)
        }
        Spacer(Modifier.height(12.dp))
        GlossyButton(
            "MENU", Brush.verticalGradient(listOf(Color(0xFF4FD1C5), Color(0xFF1E8C84), Color(0xFF0E5A55))),
            model::leave, Modifier.fillMaxWidth(), icon = Icons.Default.Home, height = 52.dp,
        )
    }
}
