package com.bbcl.carromoffline

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameMillis
import kotlin.math.sin
import kotlin.random.Random

val Night = Color(0xFF0A2226)
val Ink = Color(0xFF2B160C)
val Teal = Color(0xFF2EC4B6)
val Gold = Color(0xFFFFC233)
val GoldBrush = Brush.verticalGradient(listOf(Color(0xFFFFE89A), Color(0xFFFFC233), Color(0xFFE59400)))
val GreenBrush = Brush.verticalGradient(listOf(Color(0xFF7DEB8C), Color(0xFF23B04E), Color(0xFF117A34)))
val FrameBrush = Brush.linearGradient(listOf(Color(0xFFFFE08A), Color(0xFFD99A1E), Color(0xFFFFE9A8), Color(0xFFB87A10)))
val GlassFill = Color.White.copy(alpha = 0.08f)
val GlassLine = Color.White.copy(alpha = 0.16f)

/** Dark teal room with a warm spotlight where the table stands and a few soft out-of-focus lights. */
@Composable
fun GameBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF123A40), Night, Color(0xFF04100F))))
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xFFFFB45E).copy(alpha = 0.30f), Color(0xFFFF8A3D).copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width / 2, size.height * 0.48f),
                        radius = size.maxDimension * 0.6f,
                    ),
                )
                val rnd = Random(11)
                repeat(14) {
                    val c = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)
                    val r = (18 + rnd.nextFloat() * 60).dp.toPx()
                    drawCircle(
                        Brush.radialGradient(listOf(Color(0xFF7FE0D0).copy(alpha = 0.07f), Color.Transparent), c, r),
                        r, c,
                    )
                }
                // Vignette.
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                        center = Offset(size.width / 2, size.height / 2),
                        radius = size.maxDimension * 0.75f,
                    ),
                )
            },
        content = content,
    )
}

/** Frosted panel used for cards on the dark background. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .shadow(12.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .background(Brush.verticalGradient(listOf(Color(0xFF17403F), Color(0xFF0C2629))), shape)
            .border(1.dp, Brush.verticalGradient(listOf(Teal.copy(alpha = 0.55f), Teal.copy(alpha = 0.1f))), shape)
            .padding(18.dp),
        content = content,
    )
}

/** Chunky game button: gradient fill, glossy top, darker bottom lip, and it squashes when pressed. */
@Composable
fun GlossyButton(
    text: String,
    brush: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    textColor: Color = Color.White,
    height: Dp = 60.dp,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "press")
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .height(height)
            .scale(scale)
            .shadow(10.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .background(brush, shape)
            .drawBehind {
                drawRect(Color.Black.copy(alpha = 0.18f), Offset(0f, size.height - 5.dp.toPx()), Size(size.width, 5.dp.toPx()))
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent), endY = size.height * 0.5f),
                    Offset(6.dp.toPx(), 3.dp.toPx()),
                    Size(size.width - 12.dp.toPx(), size.height * 0.42f),
                    androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                )
            }
            .border(2.dp, Color.White.copy(alpha = 0.35f), shape)
            .clickable(source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                Icon(icon, null, tint = textColor, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                color = textColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                maxLines = 1,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.3f), Offset(0f, 3f), 4f)),
            )
        }
    }
}

/** A carrom coin (or the striker) seen from above: turned rings, a soft shine and a shadow. */
fun DrawScope.drawCoin(center: Offset, r: Float, kind: Kind, accent: Color = Gold, shadowAlpha: Float = 0.4f) {
    // Contact shadow and a softer cast shadow, as if lit from the top left.
    drawCircle(Color.Black.copy(alpha = shadowAlpha * 0.6f), r * 1.12f, center + Offset(r * 0.14f, r * 0.26f))
    drawCircle(Color.Black.copy(alpha = shadowAlpha), r * 1.0f, center + Offset(r * 0.06f, r * 0.12f))
    if (kind == Kind.STRIKER) {
        drawStriker(center, r, accent)
        return
    }
    val (light, base, deep) = when (kind) {
        Kind.WHITE -> Triple(Color(0xFFFFFFFB), Color(0xFFF1E6CC), Color(0xFFB8A27A))
        Kind.BLACK -> Triple(Color(0xFF6B5444), Color(0xFF2A1A12), Color(0xFF0E0806))
        else -> Triple(Color(0xFFFF8A80), Color(0xFFE0241E), Color(0xFF7E0C08))
    }
    // Rim: darker edge so the coin reads as a thick disc.
    drawCircle(Brush.linearGradient(listOf(light, deep), center - Offset(r, r), center + Offset(r, r)), r, center)
    drawCircle(Brush.radialGradient(listOf(light, base, base, deep), center - Offset(r * 0.3f, r * 0.35f), r * 1.5f), r * 0.86f, center)
    // Carved rings.
    val groove = if (kind == Kind.BLACK) Color.Black.copy(alpha = 0.55f) else deep.copy(alpha = 0.55f)
    val lip = Color.White.copy(alpha = if (kind == Kind.BLACK) 0.18f else 0.6f)
    for (k in listOf(0.68f, 0.42f)) {
        drawCircle(groove, r * k, center, style = Stroke(r * 0.06f))
        drawCircle(lip, r * k, center + Offset(r * 0.03f, r * 0.04f), style = Stroke(r * 0.03f))
    }
    if (kind == Kind.QUEEN) {
        drawCircle(GoldBrush, r * 0.24f, center)
        drawCircle(Color.White.copy(alpha = 0.6f), r * 0.08f, center - Offset(r * 0.06f, r * 0.06f))
    }
    // Specular highlight.
    drawOval(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.75f), Color.Transparent), center - Offset(r * 0.38f, r * 0.45f), r * 0.5f),
        center - Offset(r * 0.8f, r * 0.8f), Size(r * 0.9f, r * 0.6f),
    )
}

/** The striker: ivory body, a royal-blue ring with a gold rim, and a gold star in the middle. */
private fun DrawScope.drawStriker(center: Offset, r: Float, accent: Color) {
    drawCircle(Brush.linearGradient(listOf(Color.White, Color(0xFF9AA6C8)), center - Offset(r, r), center + Offset(r, r)), r, center)
    drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFEDEFF7), Color(0xFFC9CFE3)), center - Offset(r * 0.3f, r * 0.35f), r * 1.4f), r * 0.9f, center)
    val blue = Brush.radialGradient(listOf(Color(0xFF5B8CFF), Color(0xFF1E3FAE), Color(0xFF0D2270)), center - Offset(r * 0.2f, r * 0.2f), r)
    drawCircle(blue, r * 0.72f, center)
    drawCircle(accent, r * 0.72f, center, style = Stroke(r * 0.08f))
    drawCircle(accent.copy(alpha = 0.7f), r * 0.5f, center, style = Stroke(r * 0.04f))
    val star = Path()
    for (k in 0 until 10) {
        val rr = if (k % 2 == 0) r * 0.4f else r * 0.17f
        val a = -Math.PI / 2 + k * Math.PI / 5
        val p = center + Offset((rr * Math.cos(a)).toFloat(), (rr * Math.sin(a)).toFloat())
        if (k == 0) star.moveTo(p.x, p.y) else star.lineTo(p.x, p.y)
    }
    star.close()
    drawPath(star, GoldBrush)
    drawOval(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.8f), Color.Transparent), center - Offset(r * 0.4f, r * 0.45f), r * 0.55f),
        center - Offset(r * 0.85f, r * 0.85f), Size(r * 0.95f, r * 0.6f),
    )
}

@Composable
fun CoinIcon(kind: Kind, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawCoin(Offset(size.width / 2, size.height * 0.46f), size.minDimension * 0.44f, kind, shadowAlpha = 0.25f) }
}

/** Falling confetti over the whole screen, for the winner. */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val colors = listOf(Color(0xFFFFC233), Color(0xFFEF3B36), Color.White, Color(0xFF1FAE4F), Color(0xFF1F7FF0), Color(0xFFFF6FD8))
    val pieces = remember {
        List(90) {
            floatArrayOf(Random.nextFloat(), Random.nextFloat(), 0.12f + Random.nextFloat() * 0.18f, Random.nextFloat() * 6.28f, Random.nextFloat())
        }
    }
    val time = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { time.longValue = it - start }
    }
    Canvas(modifier.fillMaxSize()) {
        val t = time.longValue / 1000f
        pieces.forEachIndexed { i, (x0, y0, speed, phase, kind) ->
            val y = ((y0 + t * speed) % 1.1f - 0.05f) * size.height
            val x = (x0 + 0.04f * sin(t * 2 + phase)) * size.width
            val s = 7.dp.toPx()
            rotate(t * 160 + phase * 57, Offset(x, y)) {
                val color = colors[i % colors.size]
                if (kind > 0.5f) drawRect(color, Offset(x - s / 2, y - s / 4), Size(s, s / 2))
                else drawCircle(color, s / 3, Offset(x, y))
            }
        }
    }
}

/** Title text filled with the gold gradient. */
@Composable
fun GoldText(text: String, size: Int, modifier: Modifier = Modifier, spacing: Float = 2f) {
    Text(
        text,
        modifier = modifier,
        style = TextStyle(
            brush = GoldBrush,
            fontSize = size.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = spacing.sp,
            shadow = Shadow(Color(0xFF7A3E00), Offset(0f, 5f), 2f),
        ),
    )
}

/** Small frosted pill used for status lines and labels. */
@Composable
fun Pill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier.background(Color.Black.copy(alpha = 0.25f), shape).border(1.dp, GlassLine, shape).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, color = Teal, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
}
