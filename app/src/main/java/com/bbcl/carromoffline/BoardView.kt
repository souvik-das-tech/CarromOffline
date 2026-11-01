package com.bbcl.carromoffline

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Width of the wooden frame drawn round the playing surface, in millimetres. */
private const val FRAME = 62f
private const val TOTAL = Table.SIZE + 2 * FRAME

private val Lines = Color(0xFF2A1A10)
private val Red = Color(0xFFC62828)
val Brass = Brush.linearGradient(listOf(Color(0xFFFFE7A3), Color(0xFFC9922E), Color(0xFFFFF0C2), Color(0xFF8E5E12)))

private fun Vec.o() = Offset(x, y)

/** Turns a board point [quarters] quarter turns clockwise round the middle of the board. */
private fun turn(v: Vec, quarters: Int): Vec {
    var x = v.x - Table.CENTER
    var y = v.y - Table.CENTER
    repeat(((quarters % 4) + 4) % 4) {
        val nx = -y
        y = x
        x = nx
    }
    return Vec(x + Table.CENTER, y + Table.CENTER)
}

/** Pull-back distance (mm on the board) that gives a full-power shot. */
private const val FULL_PULL = 250f

/** The live board: the table, the coins as they slide, and the aiming guide while a shot is lined up. */
@Composable
fun CarromBoard(model: GameModel, modifier: Modifier = Modifier) {
    var aim by remember { mutableStateOf<Shot?>(null) }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "pulse",
    )
    val text = rememberTextMeasurer()

    Canvas(
        modifier
            .aspectRatio(1f)
            .shadow(30.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black, spotColor = Color.Black)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val g = model.game
                    if (g == null || !model.canAim) return@awaitEachGesture
                    val scale = min(size.width, size.height) / TOTAL
                    val view = model.viewSeat
                    // Back from the turned picture to board millimetres.
                    fun table(o: Offset) = turn(Vec(o.x / scale - FRAME, o.y / scale - FRAME), 4 - view)
                    val start = table(down.position)
                    // Any drag on the board pulls back like a slingshot. Starting on the striker, it aims
                    // from the striker itself; elsewhere, the pull is measured from where the finger went down.
                    val anchor = if ((start - model.striker.pos).length() < Table.STRIKER_R * 2.4f) model.striker.pos else start
                    var shot: Shot? = null
                    var moved = false
                    drag(down.id) { change ->
                        val p = table(change.position)
                        if ((p - start).length() > 12f) moved = true
                        val pull = anchor - p
                        shot = Shot(model.strikerT, pull.unit(), (pull.length() / FULL_PULL).coerceAtMost(1f))
                        aim = shot
                        model.aimChanged(shot)
                        change.consume()
                    }
                    aim = null
                    model.aimChanged(null)
                    if (!moved) {
                        // A tap on your own baseline puts the striker there.
                        if ((Table.strikerAt(g.currentSeat, Table.tFor(g.currentSeat, start)) - start).length() < 45f) {
                            model.moveStriker(Table.tFor(g.currentSeat, start))
                        }
                    } else {
                        shot?.let { if (it.power > 0.06f) model.shootHuman(it.dir, it.power) }
                    }
                }
            },
    ) {
        // Read the frame counter here, in the draw phase, so every physics step redraws the board.
        model.frame
        val game = model.game
        val s = size.minDimension / TOTAL
        val view = model.viewSeat
        withTransform({
            scale(s, s, Offset.Zero)
            translate(FRAME, FRAME)
            rotate(90f * view, Offset(Table.CENTER, Table.CENTER))
        }) {
            drawTable()
            val idle = model.phase != Phase.MOVING && model.phase != Phase.SETTLING
            if (game != null && !game.over && idle) {
                rotate(-90f * game.currentSeat, Offset(Table.CENTER, Table.CENTER)) {
                    val y = Table.SIZE - Table.BASE_FROM_EDGE
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(Color.Transparent, Teal.copy(alpha = 0.55f), Teal.copy(alpha = 0.55f), Color.Transparent), Table.CENTER - 260, Table.CENTER + 260),
                        Offset(Table.CENTER - 260, y - 24), Size(520f, 48f), CornerRadius(24f),
                    )
                }
                // Ripple round the striker while it waits to be played.
                val p = model.striker.pos.o()
                drawCircle(Teal.copy(alpha = 0.6f * (1 - pulse)), Table.STRIKER_R * (1.1f + 0.9f * pulse), p, style = Stroke(4f))
            }
            val shown = aim ?: model.aiAim
            if (shown != null && shown.power > 0.02f) drawGuide(model.discs, model.striker, shown)

            // Motion trails behind fast discs.
            for (d in model.discs) {
                val trail = model.trails[d.id] ?: continue
                if (d.pocketed || trail.size < 2) continue
                val color = when (d.kind) {
                    Kind.WHITE -> Color(0xFFFFF6DE)
                    Kind.BLACK -> Color(0xFF3A2A20)
                    Kind.QUEEN -> Color(0xFFFF5A52)
                    Kind.STRIKER -> Color(0xFF8FB4FF)
                }
                trail.forEachIndexed { i, v ->
                    val a = (i + 1f) / trail.size
                    drawCircle(color.copy(alpha = 0.22f * a), d.r * (0.55f + 0.4f * a), v.o())
                }
            }
            for (d in model.discs) {
                if (d.pocketed) continue
                val accent = if (d.kind == Kind.STRIKER && !model.strikerFits() && model.phase == Phase.AIM) Red else Gold
                drawCoin(d.pos.o(), d.r, d.kind, accent)
            }
            for (f in model.falling) {
                val p = ((model.clock - f.start) / 0.3f).coerceIn(0f, 1f)
                if (p >= 1f) continue
                val at = f.from + (f.to - f.from) * p
                drawCoin(at.o(), (if (f.kind == Kind.STRIKER) Table.STRIKER_R else Table.COIN_R) * (1 - 0.55f * p), f.kind, shadowAlpha = 0f)
            }
        }
        // Labels are drawn upright, at the turned pocket positions.
        withTransform({
            scale(s, s, Offset.Zero)
            translate(FRAME, FRAME)
        }) {
            for (b in model.bursts) drawBurst(b.copy(at = turn(b.at, view)), model.clock - b.start, text)
        }
    }
}

/** The board with the coins in their starting places, for the menu. */
@Composable
fun BoardPreview(modifier: Modifier = Modifier) {
    val coins = remember { Table.startingCoins() }
    Canvas(modifier.aspectRatio(1f).shadow(24.dp, RoundedCornerShape(18.dp))) {
        val s = size.minDimension / TOTAL
        withTransform({
            scale(s, s, Offset.Zero)
            translate(FRAME, FRAME)
        }) {
            drawTable()
            coins.forEach { drawCoin(it.pos.o(), it.r, it.kind) }
            val p = Table.strikerAt(0, 0f)
            drawCoin(p.o(), Table.STRIKER_R, Kind.STRIKER)
        }
    }
}

/** Gold ring spreading out of a pocket, and a label floating up. */
private fun DrawScope.drawBurst(b: Burst, age: Float, text: TextMeasurer) {
    if (age < 0f || age > 0.9f) return
    val t = age / 0.9f
    val color = Color(b.color)
    drawCircle(color.copy(alpha = 0.7f * (1 - t)), Table.POCKET_R * (1f + 2.6f * t), b.at.o(), style = Stroke(6f * (1 - t) + 1f))
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.55f * (1 - t)), Color.Transparent), b.at.o(), Table.POCKET_R * 2.4f), Table.POCKET_R * 2.4f, b.at.o())
    val layout = text.measure(b.label, TextStyle(color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black))
    // Float the label in towards the board so it never sits off the edge.
    val inward = (Vec(Table.CENTER, Table.CENTER) - b.at).unit()
    val at = b.at + inward * (60f + 40f * t)
    val topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f - 30f * t)
    drawText(layout, color = Color.Black.copy(alpha = 0.5f * (1 - t)), topLeft = topLeft + Offset(2f, 3f))
    drawText(layout, color = color.copy(alpha = 1 - t * t), topLeft = topLeft)
}

/** Frame, surface, pockets and the printed markings. Drawn in board millimetres. */
private fun DrawScope.drawTable() {
    val size = Table.SIZE
    val outer = Offset(-FRAME, -FRAME)
    // Mahogany frame: base colour, grain, then a bevel lit from the top left.
    drawRoundRect(
        Brush.linearGradient(listOf(Color(0xFF7A2E14), Color(0xFF4A170A), Color(0xFF6A2510), Color(0xFF34100A)), outer, Offset(size + FRAME, size + FRAME)),
        outer, Size(TOTAL, TOTAL), CornerRadius(40f),
    )
    clipRect(-FRAME, -FRAME, size + FRAME, size + FRAME) {
        for (k in 0 until 34) {
            val y = -FRAME + k * TOTAL / 34 + 7 * sin(k * 1.7f)
            drawLine(Color.Black.copy(alpha = 0.1f), Offset(-FRAME, y), Offset(size + FRAME, y + 12 * cos(k * 0.9f)), 2.5f)
            drawLine(Color(0xFFFFB27A).copy(alpha = 0.04f), Offset(-FRAME, y + 4), Offset(size + FRAME, y + 4 + 12 * cos(k * 0.9f)), 1.5f)
        }
    }
    drawRoundRect(
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent, Color.Black.copy(alpha = 0.4f)), outer, Offset(size + FRAME, size + FRAME)),
        outer, Size(TOTAL, TOTAL), CornerRadius(40f), style = Stroke(10f),
    )
    drawRoundRect(Brass, Offset(-FRAME + 12, -FRAME + 12), Size(TOTAL - 24, TOTAL - 24), CornerRadius(30f), style = Stroke(3f))
    // Inner cushion edge, in shadow.
    drawRect(Color.Black.copy(alpha = 0.55f), Offset(-10f, -10f), Size(size + 20, size + 20))
    drawRect(Brush.verticalGradient(listOf(Color(0xFF2A0C05), Color(0xFF5A2410)), -10f, 0f), Offset(-10f, -10f), Size(size + 20, 10f))

    // Birch surface: warm light in the middle, grain, and darker towards the cushions.
    drawRect(Brush.radialGradient(listOf(Color(0xFFFFF1D2), Color(0xFFF4D9A4), Color(0xFFE2BC7C)), Offset(size / 2, size * 0.45f), size * 0.8f), Offset.Zero, Size(size, size))
    for (k in 0 until 60) {
        val y = k * size / 60 + 4 * sin(k * 2.3f)
        drawLine(Color(0xFF9A6A30).copy(alpha = if (k % 3 == 0) 0.08f else 0.04f), Offset(0f, y), Offset(size, y + 9 * sin(k * 0.7f)), if (k % 3 == 0) 2.5f else 1.5f)
    }
    drawRect(
        Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, Color(0xFF7A4A1A).copy(alpha = 0.28f)), Offset(size / 2, size / 2), size * 0.72f),
        Offset.Zero, Size(size, size),
    )

    for (p in Table.pockets) {
        val c = p.o()
        drawCircle(Brass, Table.POCKET_R + 9, c)
        drawCircle(Color.Black.copy(alpha = 0.5f), Table.POCKET_R + 9, c, style = Stroke(2f))
        drawCircle(Brush.radialGradient(listOf(Color.Black, Color(0xFF1A0E08), Color(0xFF3A2414)), c, Table.POCKET_R + 3), Table.POCKET_R + 3, c)
        // Net: a few strands across the hole.
        for (k in -2..2) {
            val d = k * 7f
            val h = sqrt(((Table.POCKET_R + 2) * (Table.POCKET_R + 2) - d * d).coerceAtLeast(0f))
            drawLine(Color.White.copy(alpha = 0.12f), c + Offset(d, -h), c + Offset(d, h), 1.4f)
            drawLine(Color.White.copy(alpha = 0.12f), c + Offset(-h, d), c + Offset(h, d), 1.4f)
        }
    }

    for (seat in 0..3) {
        rotate(-90f * seat, Offset(size / 2, size / 2)) {
            // Baseline: two lines with a red circle at each end.
            val yMid = size - Table.BASE_FROM_EDGE
            for (dy in listOf(-17f, 17f)) drawLine(Lines, Offset(size / 2 - Table.BASE_HALF, yMid + dy), Offset(size / 2 + Table.BASE_HALF, yMid + dy), if (dy < 0) 1.8f else 3f)
            for (dx in listOf(-Table.BASE_HALF, Table.BASE_HALF)) {
                val c = Offset(size / 2 + dx, yMid)
                drawCircle(Color(0xFFF4D9A4), 18f, c)
                drawCircle(Lines, 18f, c, style = Stroke(2.2f))
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFF5A4E), Red, Color(0xFF8E1410)), c - Offset(4f, 4f), 16f), 13f, c)
            }
            // Diagonal arrow from the bottom-left pocket towards the middle, ending in a curl.
            val from = Offset(Table.POCKET_AT + 44, size - Table.POCKET_AT - 44)
            val to = Offset(from.x + 165, from.y - 165)
            drawLine(Lines, from, to, 2.2f)
            val head = Path().apply {
                moveTo(from.x - 7, from.y + 7)
                lineTo(from.x + 15, from.y + 3)
                lineTo(from.x - 3, from.y - 15)
                close()
            }
            drawPath(head, Lines)
            drawArc(Lines, 135f, 200f, false, Offset(to.x - 24, to.y - 24), Size(48f, 48f), style = Stroke(2.2f))
        }
    }

    // Centre: double circle with a sixteen-point rose, petals alternating red and black.
    val c = Offset(size / 2, size / 2)
    drawCircle(Lines, 90f, c, style = Stroke(3f))
    drawCircle(Lines, 83f, c, style = Stroke(1.4f))
    for (k in 0 until 16) {
        val a = -PI / 2 + k * PI / 8
        val r = if (k % 2 == 0) 78f else 56f
        val tip = c + Offset(r * cos(a).toFloat(), r * sin(a).toFloat())
        val l = c + Offset(22f * cos(a - 0.38).toFloat(), 22f * sin(a - 0.38).toFloat())
        val rr = c + Offset(22f * cos(a + 0.38).toFloat(), 22f * sin(a + 0.38).toFloat())
        val petal = Path().apply {
            moveTo(l.x, l.y)
            lineTo(tip.x, tip.y)
            lineTo(rr.x, rr.y)
            close()
        }
        drawPath(petal, if (k % 2 == 0) Red else Color(0xFF2A1A10))
    }
    drawCircle(Color(0xFFF4D9A4), 22f, c)
    drawCircle(Red, 22f, c, style = Stroke(3f))
    drawCircle(Red, 7f, c)
}

/**
 * Aiming guide: a bright arrow from the striker to the first coin or cushion it would meet, a
 * ghost striker where it touches, and the direction the hit coin will go. A ring round the
 * striker fills up with the power.
 */
private fun DrawScope.drawGuide(discs: List<Disc>, striker: Disc, shot: Shot) {
    val s = striker.pos
    val d = shot.dir
    var best = Float.MAX_VALUE
    var hit: Disc? = null
    for (c in discs) {
        if (c === striker || c.pocketed) continue
        val reach = c.r + striker.r
        val f = s - c.pos
        val b = d.dot(f)
        val disc = b * b - (f.dot(f) - reach * reach)
        if (disc < 0) continue
        val t = -b - sqrt(disc)
        if (t > 0 && t < best) {
            best = t
            hit = c
        }
    }
    val r = striker.r
    val wall = listOf(
        if (d.x > 0) (Table.SIZE - r - s.x) / d.x else if (d.x < 0) (r - s.x) / d.x else Float.MAX_VALUE,
        if (d.y > 0) (Table.SIZE - r - s.y) / d.y else if (d.y < 0) (r - s.y) / d.y else Float.MAX_VALUE,
    ).min()
    val power = shot.power
    val heat = Color(red = 0.35f + 0.65f * power, green = 0.95f - 0.6f * power, blue = 0.35f - 0.2f * power)
    // Pull-back side: a fading tail behind the striker, longer for a harder shot.
    val tail = s - d * (40f + 140f * power)
    drawLine(Brush.linearGradient(listOf(heat.copy(alpha = 0.7f), Color.Transparent), s.o(), tail.o()), s.o(), tail.o(), 2 * r * 0.55f, StrokeCap.Round)

    val end = if (hit != null && best < wall) s + d * best else s + d * wall
    val glow = Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = 0.6f)), s.o(), end.o())
    drawLine(Color.Black.copy(alpha = 0.25f), (s + d * r).o() + Offset(2f, 3f), end.o() + Offset(2f, 3f), 8f, StrokeCap.Round)
    drawLine(glow, (s + d * r).o(), end.o(), 6f, StrokeCap.Round)
    if (hit != null && best < wall) {
        drawCircle(Color.White.copy(alpha = 0.25f), r, end.o())
        drawCircle(Color.White, r, end.o(), style = Stroke(3.5f))
        val out = (hit.pos - end).unit()
        val tip = hit.pos + out * 130f
        drawLine(Gold, hit.pos.o(), tip.o(), 6f, StrokeCap.Round)
        drawArrowHead(tip, out, Gold)
    } else {
        val bounce = if (end.x <= r + 0.5f || end.x >= Table.SIZE - r - 0.5f) Vec(-d.x, d.y) else Vec(d.x, -d.y)
        val tip = end + bounce * 100f
        drawLine(Color.White.copy(alpha = 0.5f), end.o(), tip.o(), 4f, StrokeCap.Round)
        drawArrowHead(tip, bounce, Color.White.copy(alpha = 0.6f))
    }
    drawCircle(Color.Black.copy(alpha = 0.35f), r + 14, s.o(), style = Stroke(8f))
    drawArc(heat, -90f, 360f * power, false, Offset(s.x - r - 14, s.y - r - 14), Size(2 * r + 28, 2 * r + 28), style = Stroke(8f, cap = StrokeCap.Round))
}

private fun DrawScope.drawArrowHead(tip: Vec, dir: Vec, color: Color) {
    val side = Vec(-dir.y, dir.x)
    val back = tip - dir * 22f
    val head = Path().apply {
        moveTo(tip.x + dir.x * 6, tip.y + dir.y * 6)
        lineTo(back.x + side.x * 14, back.y + side.y * 14)
        lineTo(back.x - side.x * 14, back.y - side.y * 14)
        close()
    }
    drawPath(head, color)
}
