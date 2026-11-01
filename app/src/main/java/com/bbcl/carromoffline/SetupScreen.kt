package com.bbcl.carromoffline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val seatNames = listOf("Bottom", "Right", "Top", "Left")

@Composable
fun SetupScreen(model: GameModel) {
    GameBackground {
        Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Logo()
                Spacer(Modifier.height(18.dp))
                GlossyButton(
                    "PLAY ON BLUETOOTH",
                    Brush.verticalGradient(listOf(Color(0xFF7DBBFF), Color(0xFF1F7FF0), Color(0xFF0B48A6))),
                    model::openLobby,
                    Modifier.fillMaxWidth(),
                    icon = Icons.Default.Bluetooth,
                    height = 54.dp,
                )
                Text(
                    "Each player on their own phone · no internet",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(20.dp))

                model.savedGame?.let { saved ->
                    GlossyButton("CONTINUE", GreenBrush, model::continueSaved, Modifier.fillMaxWidth(), icon = Icons.Default.PlayArrow)
                    Text(
                        "${saved.teamName(0)} vs ${saved.teamName(1)}  ·  ${saved.pocketed(0)}–${saved.pocketed(1)}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Spacer(Modifier.height(20.dp))
                }

                GlassCard(Modifier.fillMaxWidth()) {
                    SectionLabel("GAME")
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModeChip("1 vs 1", "2 PLAYERS", model.playerCount == 2, Modifier.weight(1f)) { model.playerCount = 2 }
                        ModeChip("2 vs 2", "4 PLAYERS · TEAMS", model.playerCount == 4, Modifier.weight(1f)) { model.playerCount = 4 }
                    }
                    Spacer(Modifier.height(20.dp))
                    SectionLabel("PLAYERS")
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (i in 0 until model.playerCount) PlayerRow(model, i)
                    }
                    if (model.playerCount == 4) {
                        Text(
                            "Partners sit opposite each other: White team bottom & top, Black team right & left.",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                GlossyButton(
                    if (model.savedGame == null) "PLAY" else "NEW GAME",
                    GoldBrush, model::startNew, Modifier.fillMaxWidth(), textColor = Ink, height = 66.dp,
                )
                Spacer(Modifier.height(20.dp))
                HowToPlay()
            }
        }
    }
}

/** The board, gently floating and tilted in a warm glow, over the gold title. */
@Composable
private fun Logo() {
    val float by rememberInfiniteTransition(label = "logo").animateFloat(
        -5f, 5f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "float",
    )
    Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFB45E).copy(alpha = 0.45f), Color.Transparent)), size.minDimension / 2)
            },
        )
        BoardPreview(Modifier.size(160.dp).offset(y = float.dp).rotate(-8f))
        CoinIcon(Kind.STRIKER, Modifier.size(44.dp).align(Alignment.BottomEnd).offset(x = (-14).dp, y = (-18 - float).dp))
        CoinIcon(Kind.QUEEN, Modifier.size(30.dp).align(Alignment.TopStart).offset(x = 18.dp, y = (22 + float).dp))
    }
    GoldText("CARROM", 52, spacing = 4f)
    Text(
        "O F F L I N E",
        color = Teal,
        fontSize = 15.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 4.sp,
        modifier = Modifier.offset(y = (-4).dp),
    )
    Text(
        "1 vs 1 or 2 vs 2 · one phone or Bluetooth",
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun ModeChip(title: String, subtitle: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val four = title.startsWith("2")
    Column(
        modifier
            .height(112.dp)
            .shadow(if (selected) 10.dp else 0.dp, shape, spotColor = Gold)
            .background(if (selected) GoldBrush else Brush.verticalGradient(listOf(Color(0xFF1C4A48), Color(0xFF0F2E31))), shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) Color.White.copy(alpha = 0.8f) else Teal.copy(alpha = 0.3f), shape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Little picture of who plays: two coins facing off, or two pairs.
        Row(verticalAlignment = Alignment.CenterVertically) {
            val size = if (four) 20.dp else 26.dp
            CoinIcon(Kind.WHITE, Modifier.size(size))
            if (four) CoinIcon(Kind.WHITE, Modifier.size(size).offset(x = (-6).dp))
            Text("VS", color = if (selected) Ink else Gold, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 6.dp))
            CoinIcon(Kind.BLACK, Modifier.size(size))
            if (four) CoinIcon(Kind.BLACK, Modifier.size(size).offset(x = (-6).dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(title, color = if (selected) Ink else Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = if (selected) Ink.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
private fun PlayerRow(model: GameModel, i: Int) {
    val cpu = model.computer[i]
    val kind = teamKind(i % 2)
    val seat = if (model.playerCount == 2) i * 2 else i
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.25f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(46.dp)) {
            val palette = if (kind == Kind.WHITE) listOf(Color(0xFFFFE0B2), Color(0xFFE08A3C), Color(0xFF8E3E12))
            else listOf(Color(0xFF90A4AE), Color(0xFF455A64), Color(0xFF1C2A30))
            Box(
                Modifier
                    .size(42.dp)
                    .background(Brush.linearGradient(palette), CircleShape)
                    .border(2.dp, Brush.verticalGradient(listOf(Color(0xFFFFE89A), Color(0xFFB87A10))), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (cpu) Icon(Icons.Default.SmartToy, null, tint = Color.White, modifier = Modifier.size(22.dp))
                else Text(model.names[i].ifEmpty { "P" }.take(1).uppercase(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
            CoinIcon(kind, Modifier.size(18.dp).align(Alignment.BottomEnd))
        }
        Column(Modifier.weight(1f)) {
            Text(
                "${seatNames[seat]} · ${if (kind == Kind.WHITE) "White" else "Black"}".uppercase(),
                color = Teal.copy(alpha = 0.8f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            val name = model.names[i]
            BasicTextField(
                value = name,
                onValueChange = { model.names[i] = it.take(14) },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Column {
                        Box {
                            if (name.isEmpty()) Text("Player ${i + 1}", color = Color.White.copy(alpha = 0.4f), fontSize = 17.sp)
                            inner()
                        }
                        Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.2f)))
                    }
                },
            )
        }
        val toggle = RoundedCornerShape(50)
        Row(
            Modifier
                .width(116.dp)
                .background(
                    if (cpu) Brush.verticalGradient(listOf(Color(0xFF4FD1C5), Color(0xFF1E8C84))) else SolidColor(Color.Black.copy(alpha = 0.3f)),
                    toggle,
                )
                .border(1.dp, if (cpu) Color.White.copy(alpha = 0.5f) else GlassLine, toggle)
                .clickable { model.computer[i] = !cpu }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(if (cpu) Icons.Default.SmartToy else Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (cpu) "Computer" else "Player", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HowToPlay() {
    var open by rememberSaveable { mutableStateOf(false) }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("HOW TO PLAY")
            Spacer(Modifier.weight(1f))
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Color.White)
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    "👆" to "Move the striker with the wooden rail under the board, or tap a spot on your glowing baseline.",
                    "🎯" to "Touch the striker and pull back, like a slingshot. The arrow shows where it goes; pull further for more power. Let go to shoot.",
                    "⚪" to "White plays first. Pocket a coin of your colour to shoot again.",
                    "🔴" to "The red queen is worth 3, but you must cover it: pocket one of your own coins right after (or in the same shot), or it goes back.",
                    "⚠️" to "Striker in a pocket is a foul: you lose a coin back to the centre.",
                    "🏆" to "Pocket all 9 of your coins, after the queen is covered, to win.",
                ).forEach { (icon, text) ->
                    Row {
                        Text(icon, fontSize = 16.sp, modifier = Modifier.width(30.dp))
                        Text(text, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
